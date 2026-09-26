package ds.mods.CCLights2.gpu.shader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tokenizer for the GLSL subset. Handles comments, object-like #define macros and skips other
 * preprocessor lines.
 */
public final class Lexer {
	public enum T { IDENT, INT, FLOAT, OP, EOF }

	public static final class Token {
		public final T type;
		public final String text;
		public final int line;

		Token(T type, String text, int line) {
			this.type = type;
			this.text = text;
			this.line = line;
		}

		public boolean is(String s) {
			return (type == T.OP || type == T.IDENT) && text.equals(s);
		}

		@Override
		public String toString() {
			return type == T.EOF ? "end of source" : "'" + text + "'";
		}
	}

	private static final String[] OPS = {
		"<<=", ">>=", "++", "--", "<=", ">=", "==", "!=", "&&", "||", "^^", "+=", "-=", "*=", "/=",
		"+", "-", "*", "/", "%", "<", ">", "=", "!", "?", ":", ";", ",", ".", "(", ")", "[", "]", "{", "}", "&", "|", "^", "~"
	};

	private final String src;
	private int pos = 0;
	private int line = 1;
	private final Map<String, List<Token>> macros = new HashMap<String, List<Token>>();

	public Lexer(String src) {
		this.src = src;
	}

	public List<Token> tokenize() throws ShaderException {
		List<Token> out = new ArrayList<Token>();
		while (true) {
			Token t = next();
			if (t == null) continue;
			if (t.type == T.EOF) {
				out.add(t);
				return out;
			}
			if (t.type == T.IDENT && macros.containsKey(t.text)) {
				for (Token m : macros.get(t.text)) out.add(new Token(m.type, m.text, t.line));
				continue;
			}
			out.add(t);
		}
	}

	private boolean atLineStart() {
		int i = pos - 1;
		while (i >= 0 && (src.charAt(i) == ' ' || src.charAt(i) == '\t')) i--;
		return i < 0 || src.charAt(i) == '\n';
	}

	private Token next() throws ShaderException {
		while (pos < src.length()) {
			char c = src.charAt(pos);
			if (c == '\n') {
				line++;
				pos++;
			} else if (c == ' ' || c == '\t' || c == '\r') {
				pos++;
			} else if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '/') {
				while (pos < src.length() && src.charAt(pos) != '\n') pos++;
			} else if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '*') {
				pos += 2;
				while (pos < src.length() && !(src.charAt(pos) == '*' && pos + 1 < src.length() && src.charAt(pos + 1) == '/')) {
					if (src.charAt(pos) == '\n') line++;
					pos++;
				}
				pos = Math.min(src.length(), pos + 2);
			} else if (c == '#' && atLineStart()) {
				preprocessor();
			} else {
				break;
			}
		}
		if (pos >= src.length()) return new Token(T.EOF, "", line);
		char c = src.charAt(pos);
		int start = pos;
		if (Character.isLetter(c) || c == '_') {
			while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) pos++;
			return new Token(T.IDENT, src.substring(start, pos), line);
		}
		if (Character.isDigit(c) || (c == '.' && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1)))) {
			boolean isFloat = false;
			while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
			if (pos < src.length() && src.charAt(pos) == '.') {
				isFloat = true;
				pos++;
				while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
			}
			if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
				int save = pos;
				pos++;
				if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
				if (pos < src.length() && Character.isDigit(src.charAt(pos))) {
					isFloat = true;
					while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
				} else {
					pos = save;
				}
			}
			String text = src.substring(start, pos);
			// suffixes: 1.0f, 2u, 3.5lf
			while (pos < src.length() && "fFuUlL".indexOf(src.charAt(pos)) >= 0) {
				if (src.charAt(pos) == 'f' || src.charAt(pos) == 'F') isFloat = true;
				pos++;
			}
			return new Token(isFloat ? T.FLOAT : T.INT, text, line);
		}
		for (String op : OPS) {
			if (src.startsWith(op, pos)) {
				pos += op.length();
				return new Token(T.OP, op, line);
			}
		}
		throw new ShaderException("unexpected character '" + c + "'", line);
	}

	private void preprocessor() throws ShaderException {
		int end = src.indexOf('\n', pos);
		if (end < 0) end = src.length();
		String directive = src.substring(pos + 1, end).trim();
		pos = end;
		if (directive.startsWith("define")) {
			String rest = directive.substring(6).trim();
			int sp = 0;
			while (sp < rest.length() && (Character.isLetterOrDigit(rest.charAt(sp)) || rest.charAt(sp) == '_')) sp++;
			String name = rest.substring(0, sp);
			if (name.isEmpty()) throw new ShaderException("bad #define", line);
			if (sp < rest.length() && rest.charAt(sp) == '(') throw new ShaderException("function-like macros are not supported (" + name + ")", line);
			String body = rest.substring(sp).trim();
			int commentAt = body.indexOf("//");
			if (commentAt >= 0) body = body.substring(0, commentAt).trim();
			Lexer sub = new Lexer(body);
			sub.macros.putAll(macros);
			List<Token> toks = sub.tokenize();
			toks.remove(toks.size() - 1);
			macros.put(name, toks);
		}
		// #version, #precision, #ifdef ... are ignored; the whole subset is always available.
	}
}
