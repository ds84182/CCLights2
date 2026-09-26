package ds.mods.CCLights2.gpu.shader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ds.mods.CCLights2.gpu.shader.Ast.*;
import ds.mods.CCLights2.gpu.shader.Lexer.T;
import ds.mods.CCLights2.gpu.shader.Lexer.Token;

/** Recursive-descent parser for the GLSL subset. */
public final class Parser {
	private static final Set<String> QUALIFIERS = new HashSet<String>(Arrays.asList(
			"const", "uniform", "in", "out", "inout", "varying", "attribute", "highp", "mediump", "lowp", "flat", "smooth", "invariant"));
	private static final Set<String> PRECISION = new HashSet<String>(Arrays.asList("highp", "mediump", "lowp"));

	private final List<Token> toks;
	private int p = 0;
	private final java.util.Map<String, GType> structs = new java.util.LinkedHashMap<String, GType>();

	public Parser(List<Token> toks) {
		this.toks = toks;
	}

	public static Unit parse(String source) throws ShaderException {
		return new Parser(new Lexer(source).tokenize()).unit();
	}

	// ------------------------------------------------------------------ helpers

	private Token peek() {
		return toks.get(p);
	}

	private Token peek(int k) {
		return toks.get(Math.min(p + k, toks.size() - 1));
	}

	private Token advance() {
		Token t = toks.get(p);
		if (p < toks.size() - 1) p++;
		return t;
	}

	private boolean accept(String s) {
		if (peek().is(s)) {
			advance();
			return true;
		}
		return false;
	}

	private Token expect(String s) throws ShaderException {
		if (!peek().is(s)) throw new ShaderException("expected '" + s + "' but found " + peek(), peek().line);
		return advance();
	}

	private String expectIdent() throws ShaderException {
		if (peek().type != T.IDENT) throw new ShaderException("expected a name but found " + peek(), peek().line);
		return advance().text;
	}

	private ShaderException error(String msg) {
		return new ShaderException(msg, peek().line);
	}

	private boolean atType() {
		Token t = peek();
		return t.type == T.IDENT && (GType.parse(t.text) != null || QUALIFIERS.contains(t.text) || structs.containsKey(t.text) || t.text.equals("struct"));
	}

	/** struct Name { type field; ... } - registers the type; the caller handles trailing declarators. */
	private GType structDef() throws ShaderException {
		expect("struct");
		String name = expectIdent();
		if (GType.parse(name) != null || structs.containsKey(name)) throw error("type '" + name + "' is already defined");
		expect("{");
		List<String> names = new ArrayList<String>();
		List<GType> types = new ArrayList<GType>();
		while (!peek().is("}")) {
			if (peek().type == T.EOF) throw error("unexpected end of source inside struct " + name);
			while (peek().type == T.IDENT && QUALIFIERS.contains(peek().text)) advance();
			GType t = parseType();
			if (t.kind == GType.Kind.SAMPLER || t.kind == GType.Kind.VOID) throw error("struct fields cannot be " + t);
			do {
				String fn = expectIdent();
				GType ft = t;
				if (accept("[")) {
					ft = t.array(constInt());
					expect("]");
				}
				if (names.contains(fn)) throw error("duplicate field '" + fn + "' in struct " + name);
				names.add(fn);
				types.add(ft);
			} while (accept(","));
			expect(";");
		}
		expect("}");
		if (names.isEmpty()) throw error("struct " + name + " has no fields");
		GType st = GType.struct(new GType.StructDef(name, names.toArray(new String[0]), types.toArray(new GType[0])));
		structs.put(name, st);
		return st;
	}

	private static <N extends Node> N at(N n, int line) {
		n.line = line;
		return n;
	}

	// ------------------------------------------------------------------ top level

	private Unit unit() throws ShaderException {
		List<GlobalDecl> globals = new ArrayList<GlobalDecl>();
		List<FuncDef> funcs = new ArrayList<FuncDef>();
		while (peek().type != T.EOF) {
			if (accept(";")) continue;
			if (peek().is("precision")) {
				while (!peek().is(";") && peek().type != T.EOF) advance();
				expect(";");
				continue;
			}
			int line = peek().line;
			String qualifier = "";
			while (peek().type == T.IDENT && QUALIFIERS.contains(peek().text)) {
				String q = advance().text;
				if (!PRECISION.contains(q) && !q.equals("flat") && !q.equals("smooth") && !q.equals("invariant")) {
					if (q.equals("varying") || q.equals("attribute")) q = "in";
					qualifier = q;
				}
			}
			GType type;
			if (peek().is("struct")) {
				type = structDef();
				if (accept(";")) continue;
			} else {
				type = parseType();
			}
			String name = expectIdent();
			if (peek().is("(")) {
				if (!qualifier.isEmpty()) throw error("qualifier on a function");
				funcs.add(at(function(type, name), line));
			} else {
				// possibly several declarators: uniform float a, b;
				while (true) {
					GType t = type;
					if (accept("[")) {
						t = type.array(constInt());
						expect("]");
					}
					Expr init = accept("=") ? expression() : null;
					globals.add(at(new GlobalDecl(qualifier, at(new VarDecl(t, name, init, qualifier.equals("const")), line)), line));
					if (accept(",")) {
						name = expectIdent();
						continue;
					}
					break;
				}
				expect(";");
			}
		}
		return new Unit(globals, funcs, structs);
	}

	private int constInt() throws ShaderException {
		if (peek().type != T.INT) throw error("expected an integer array size");
		return Integer.parseInt(advance().text);
	}

	private GType parseType() throws ShaderException {
		while (peek().type == T.IDENT && PRECISION.contains(peek().text)) advance();
		Token t = peek();
		GType type = t.type == T.IDENT ? GType.parse(t.text) : null;
		if (type == null && t.type == T.IDENT) type = structs.get(t.text);
		if (type == null) throw error("expected a type but found " + t);
		advance();
		return type;
	}

	private FuncDef function(GType ret, String name) throws ShaderException {
		expect("(");
		List<Param> params = new ArrayList<Param>();
		if (!peek().is(")")) {
			if (!(peek().is("void") && peek(1).is(")"))) {
				do {
					String q = "in";
					while (peek().type == T.IDENT && QUALIFIERS.contains(peek().text)) {
						String w = advance().text;
						if (w.equals("out") || w.equals("inout") || w.equals("in")) q = w;
					}
					GType t = parseType();
					String pn = expectIdent();
					if (accept("[")) {
						t = t.array(constInt());
						expect("]");
					}
					params.add(new Param(t, pn, q));
				} while (accept(","));
			} else {
				advance();
			}
		}
		expect(")");
		if (accept(";")) throw error("function prototypes are not supported; define " + name + " directly");
		Block body = block();
		return new FuncDef(ret, name, params, body);
	}

	// ------------------------------------------------------------------ statements

	private Block block() throws ShaderException {
		int line = peek().line;
		expect("{");
		List<Stmt> body = new ArrayList<Stmt>();
		while (!peek().is("}")) {
			if (peek().type == T.EOF) throw error("unexpected end of source inside a block");
			body.add(statement());
		}
		expect("}");
		return at(new Block(body), line);
	}

	private Stmt statement() throws ShaderException {
		int line = peek().line;
		Token t = peek();
		if (t.is("{")) return block();
		if (t.is(";")) {
			advance();
			return at(new Block(new ArrayList<Stmt>()), line);
		}
		if (t.is("if")) {
			advance();
			expect("(");
			Expr cond = expression();
			expect(")");
			Stmt then = statement();
			Stmt otherwise = accept("else") ? statement() : null;
			return at(new If(cond, then, otherwise), line);
		}
		if (t.is("for")) {
			advance();
			expect("(");
			Stmt init = peek().is(";") ? null : (atType() ? declaration(false) : at(new ExprStmt(expression()), line));
			expect(";");
			Expr cond = peek().is(";") ? null : expression();
			expect(";");
			Expr step = peek().is(")") ? null : expression();
			expect(")");
			Stmt body = statement();
			return at(new For(init, cond, step, body), line);
		}
		if (t.is("while")) {
			advance();
			expect("(");
			Expr cond = expression();
			expect(")");
			return at(new While(cond, statement(), false), line);
		}
		if (t.is("do")) {
			advance();
			Stmt body = statement();
			expect("while");
			expect("(");
			Expr cond = expression();
			expect(")");
			expect(";");
			return at(new While(cond, body, true), line);
		}
		if (t.is("return")) {
			advance();
			Expr v = peek().is(";") ? null : expression();
			expect(";");
			return at(new Return(v), line);
		}
		if (t.is("break")) {
			advance();
			expect(";");
			return at(new Break(), line);
		}
		if (t.is("continue")) {
			advance();
			expect(";");
			return at(new Continue(), line);
		}
		if (t.is("discard")) {
			advance();
			expect(";");
			return at(new Discard(), line);
		}
		if (t.is("struct")) throw error("declare structs at the top level, before use");
		if (atType()) {
			Stmt d = declaration(true);
			expect(";");
			return d;
		}
		Expr e = expression();
		expect(";");
		return at(new ExprStmt(e), line);
	}

	/** One or more declarators; several become a Block so they act as one statement. */
	private Stmt declaration(boolean allowMultiple) throws ShaderException {
		int line = peek().line;
		boolean isConst = false;
		while (peek().type == T.IDENT && QUALIFIERS.contains(peek().text)) {
			if (advance().text.equals("const")) isConst = true;
		}
		GType type = parseType();
		List<VarDecl> decls = new ArrayList<VarDecl>();
		do {
			String name = expectIdent();
			GType t = type;
			if (accept("[")) {
				t = type.array(constInt());
				expect("]");
			}
			Expr init = accept("=") ? assignment() : null;
			decls.add(at(new VarDecl(t, name, init, isConst), line));
		} while (accept(","));
		if (decls.size() == 1) return decls.get(0);
		return at(new DeclGroup(decls), line);
	}

	// ------------------------------------------------------------------ expressions

	private Expr expression() throws ShaderException {
		Expr e = assignment();
		while (peek().is(",")) {
			// comma operator: evaluate both, keep the right one
			int line = advance().line;
			Expr r = assignment();
			e = at(new Binary(",", e, r), line);
		}
		return e;
	}

	private Expr assignment() throws ShaderException {
		Expr lhs = ternary();
		Token t = peek();
		if (t.is("=") || t.is("+=") || t.is("-=") || t.is("*=") || t.is("/=")) {
			advance();
			Expr rhs = assignment();
			return at(new Assign(t.text, lhs, rhs), t.line);
		}
		return lhs;
	}

	private Expr ternary() throws ShaderException {
		Expr c = binary(0);
		if (peek().is("?")) {
			int line = advance().line;
			Expr a = assignment();
			expect(":");
			Expr b = assignment();
			return at(new Ternary(c, a, b), line);
		}
		return c;
	}

	private static final String[][] LEVELS = {
		{ "||", "^^" }, { "&&" }, { "==", "!=" }, { "<", ">", "<=", ">=" }, { "+", "-" }, { "*", "/", "%" }
	};

	private Expr binary(int level) throws ShaderException {
		if (level >= LEVELS.length) return unary();
		Expr l = binary(level + 1);
		while (true) {
			Token t = peek();
			boolean matched = false;
			if (t.type == T.OP) {
				for (String op : LEVELS[level]) {
					if (t.text.equals(op)) {
						matched = true;
						break;
					}
				}
			}
			if (!matched) return l;
			advance();
			Expr r = binary(level + 1);
			l = at(new Binary(t.text, l, r), t.line);
		}
	}

	private Expr unary() throws ShaderException {
		Token t = peek();
		if (t.is("-") || t.is("+") || t.is("!")) {
			advance();
			Expr e = unary();
			return t.is("+") ? e : at(new Unary(t.text, e), t.line);
		}
		if (t.is("++") || t.is("--")) {
			advance();
			return at(new IncDec(unary(), t.is("++"), true), t.line);
		}
		return postfix();
	}

	private Expr postfix() throws ShaderException {
		Expr e = primary();
		while (true) {
			Token t = peek();
			if (t.is(".")) {
				advance();
				String name = expectIdent();
				e = at(new Member(e, name), t.line);
			} else if (t.is("[")) {
				advance();
				Expr idx = expression();
				expect("]");
				e = at(new Index(e, idx), t.line);
			} else if (t.is("++") || t.is("--")) {
				advance();
				e = at(new IncDec(e, t.is("++"), false), t.line);
			} else {
				return e;
			}
		}
	}

	private Expr primary() throws ShaderException {
		Token t = peek();
		switch (t.type) {
		case INT:
			advance();
			return at(new Literal(parseNumber(t), GType.INT), t.line);
		case FLOAT:
			advance();
			return at(new Literal(parseNumber(t), GType.FLOAT), t.line);
		case IDENT: {
			advance();
			if (t.text.equals("true")) return at(new Literal(1, GType.BOOL), t.line);
			if (t.text.equals("false")) return at(new Literal(0, GType.BOOL), t.line);
			if (peek().is("(")) {
				advance();
				List<Expr> args = new ArrayList<Expr>();
				if (!peek().is(")")) {
					do {
						args.add(assignment());
					} while (accept(","));
				}
				expect(")");
				return at(new Call(t.text, args), t.line);
			}
			if (peek().is("[") && (GType.parse(t.text) != null || structs.containsKey(t.text))) {
				// array constructor: float[3](...) - unsupported
				throw error("array constructors are not supported");
			}
			return at(new Ident(t.text), t.line);
		}
		case OP:
			if (t.is("(")) {
				advance();
				Expr e = expression();
				expect(")");
				return e;
			}
			break;
		default:
			break;
		}
		throw error("unexpected " + t);
	}

	private float parseNumber(Token t) throws ShaderException {
		try {
			if (t.type == T.INT && t.text.startsWith("0x")) return Long.parseLong(t.text.substring(2), 16);
			return (float) Double.parseDouble(t.text);
		} catch (NumberFormatException e) {
			throw new ShaderException("bad number " + t.text, t.line);
		}
	}
}
