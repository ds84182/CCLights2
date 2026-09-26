package ds.mods.CCLights2.gpu.shader;

import java.util.List;

/** Syntax tree of the GLSL subset. */
public final class Ast {
	private Ast() {}

	public abstract static class Node {
		public int line;
	}

	// ------------------------------------------------------------------ expressions

	public abstract static class Expr extends Node {}

	public static final class Literal extends Expr {
		public final float value;
		public final GType type;

		public Literal(float value, GType type) {
			this.value = value;
			this.type = type;
		}
	}

	public static final class Ident extends Expr {
		public final String name;

		public Ident(String name) {
			this.name = name;
		}
	}

	public static final class Unary extends Expr {
		public final String op;
		public final Expr e;

		public Unary(String op, Expr e) {
			this.op = op;
			this.e = e;
		}
	}

	/** ++x, --x, x++, x-- */
	public static final class IncDec extends Expr {
		public final Expr target;
		public final boolean increment;
		public final boolean prefix;

		public IncDec(Expr target, boolean increment, boolean prefix) {
			this.target = target;
			this.increment = increment;
			this.prefix = prefix;
		}
	}

	public static final class Binary extends Expr {
		public final String op;
		public final Expr l, r;

		public Binary(String op, Expr l, Expr r) {
			this.op = op;
			this.l = l;
			this.r = r;
		}
	}

	public static final class Assign extends Expr {
		/** "=", "+=", "-=", "*=", "/=" */
		public final String op;
		public final Expr target, value;

		public Assign(String op, Expr target, Expr value) {
			this.op = op;
			this.target = target;
			this.value = value;
		}
	}

	public static final class Ternary extends Expr {
		public final Expr cond, a, b;

		public Ternary(Expr cond, Expr a, Expr b) {
			this.cond = cond;
			this.a = a;
			this.b = b;
		}
	}

	public static final class Call extends Expr {
		public final String name;
		public final List<Expr> args;

		public Call(String name, List<Expr> args) {
			this.name = name;
			this.args = args;
		}
	}

	/** v.xyz */
	public static final class Member extends Expr {
		public final Expr e;
		public final String name;

		public Member(Expr e, String name) {
			this.e = e;
			this.name = name;
		}
	}

	public static final class Index extends Expr {
		public final Expr e, index;

		public Index(Expr e, Expr index) {
			this.e = e;
			this.index = index;
		}
	}

	// ------------------------------------------------------------------ statements

	public abstract static class Stmt extends Node {}

	public static final class VarDecl extends Stmt {
		public final GType type;
		public final String name;
		public final Expr init;
		public final boolean isConst;

		public VarDecl(GType type, String name, Expr init, boolean isConst) {
			this.type = type;
			this.name = name;
			this.init = init;
			this.isConst = isConst;
		}
	}

	/** Several declarators in one statement (float a = 1.0, b = 2.0;), declared in the enclosing scope. */
	public static final class DeclGroup extends Stmt {
		public final List<VarDecl> decls;

		public DeclGroup(List<VarDecl> decls) {
			this.decls = decls;
		}
	}

	public static final class ExprStmt extends Stmt {
		public final Expr e;

		public ExprStmt(Expr e) {
			this.e = e;
		}
	}

	public static final class Block extends Stmt {
		public final List<Stmt> body;

		public Block(List<Stmt> body) {
			this.body = body;
		}
	}

	public static final class If extends Stmt {
		public final Expr cond;
		public final Stmt then, otherwise;

		public If(Expr cond, Stmt then, Stmt otherwise) {
			this.cond = cond;
			this.then = then;
			this.otherwise = otherwise;
		}
	}

	public static final class For extends Stmt {
		public final Stmt init;
		public final Expr cond, step;
		public final Stmt body;

		public For(Stmt init, Expr cond, Expr step, Stmt body) {
			this.init = init;
			this.cond = cond;
			this.step = step;
			this.body = body;
		}
	}

	public static final class While extends Stmt {
		public final Expr cond;
		public final Stmt body;
		public final boolean doWhile;

		public While(Expr cond, Stmt body, boolean doWhile) {
			this.cond = cond;
			this.body = body;
			this.doWhile = doWhile;
		}
	}

	public static final class Return extends Stmt {
		public final Expr value;

		public Return(Expr value) {
			this.value = value;
		}
	}

	public static final class Break extends Stmt {}

	public static final class Continue extends Stmt {}

	public static final class Discard extends Stmt {}

	// ------------------------------------------------------------------ top level

	public static final class Param {
		public final GType type;
		public final String name;
		/** "in", "out" or "inout" */
		public final String qualifier;

		public Param(GType type, String name, String qualifier) {
			this.type = type;
			this.name = name;
			this.qualifier = qualifier;
		}
	}

	public static final class FuncDef extends Node {
		public final GType ret;
		public final String name;
		public final List<Param> params;
		public final Block body;

		public FuncDef(GType ret, String name, List<Param> params, Block body) {
			this.ret = ret;
			this.name = name;
			this.params = params;
			this.body = body;
		}
	}

	public static final class GlobalDecl extends Node {
		/** "uniform", "const", "in", "out", or "" */
		public final String qualifier;
		public final VarDecl decl;

		public GlobalDecl(String qualifier, VarDecl decl) {
			this.qualifier = qualifier;
			this.decl = decl;
		}
	}

	public static final class Unit {
		public final List<GlobalDecl> globals;
		public final List<FuncDef> functions;
		public final java.util.Map<String, GType> structs;

		public Unit(List<GlobalDecl> globals, List<FuncDef> functions, java.util.Map<String, GType> structs) {
			this.globals = globals;
			this.functions = functions;
			this.structs = structs;
		}
	}
}
