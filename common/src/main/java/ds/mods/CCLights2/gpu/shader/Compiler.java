package ds.mods.CCLights2.gpu.shader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ds.mods.CCLights2.gpu.shader.Ast.*;
import ds.mods.CCLights2.gpu.shader.GType.Kind;
import ds.mods.CCLights2.gpu.shader.Ops.Bin;
import ds.mods.CCLights2.gpu.shader.Ops.Const;
import ds.mods.CCLights2.gpu.shader.Ops.Cross;
import ds.mods.CCLights2.gpu.shader.Ops.Distance;
import ds.mods.CCLights2.gpu.shader.Ops.Dot;
import ds.mods.CCLights2.gpu.shader.Ops.Jmp;
import ds.mods.CCLights2.gpu.shader.Ops.Jz;
import ds.mods.CCLights2.gpu.shader.Ops.Length;
import ds.mods.CCLights2.gpu.shader.Ops.LoadIdx;
import ds.mods.CCLights2.gpu.shader.Ops.MatMul;
import ds.mods.CCLights2.gpu.shader.Ops.Mov;
import ds.mods.CCLights2.gpu.shader.Ops.Normalize;
import ds.mods.CCLights2.gpu.shader.Ops.Op;
import ds.mods.CCLights2.gpu.shader.Ops.Reduce;
import ds.mods.CCLights2.gpu.shader.Ops.Reflect;
import ds.mods.CCLights2.gpu.shader.Ops.Refract;
import ds.mods.CCLights2.gpu.shader.Ops.Scatter;
import ds.mods.CCLights2.gpu.shader.Ops.StoreIdx;
import ds.mods.CCLights2.gpu.shader.Ops.Swizzle;
import ds.mods.CCLights2.gpu.shader.Ops.Tern;
import ds.mods.CCLights2.gpu.shader.Ops.Tex;
import ds.mods.CCLights2.gpu.shader.Ops.Transpose;
import ds.mods.CCLights2.gpu.shader.Ops.Un;
import ds.mods.CCLights2.gpu.shader.Program.Uniform;

/**
 * Turns the syntax tree into register-machine code. Functions are inlined at every call site
 * (GLSL forbids recursion), so no call stack exists at run time.
 */
final class Compiler {
	private static final int MAX_INLINE_DEPTH = 24;

	private static final class Var {
		final GType type;
		final int reg;
		final boolean readOnly;
		final int samplerSlot;

		Var(GType type, int reg, boolean readOnly, int samplerSlot) {
			this.type = type;
			this.reg = reg;
			this.readOnly = readOnly;
			this.samplerSlot = samplerSlot;
		}
	}

	/** A materialized value: type plus the register holding it (may alias a variable). */
	private static final class Val {
		final GType type;
		final int reg;

		Val(GType type, int reg) {
			this.type = type;
			this.reg = reg;
		}
	}

	/** Something that can be assigned to. */
	private static final class LVal {
		GType type;
		int base;
		int[] comps;
		int dynIdx = -1;
		int elem, count;
		/** Offset of a struct field inside the dynamically indexed element. */
		int dynOff = 0;

		boolean plain() {
			return comps == null && dynIdx < 0;
		}
	}

	private static final class LoopCtx {
		final List<Jmp> breaks = new ArrayList<Jmp>();
		final List<Jmp> continues = new ArrayList<Jmp>();
	}

	private static final class FuncCtx {
		final GType ret;
		final int retReg;
		final List<Jmp> returns = new ArrayList<Jmp>();

		FuncCtx(GType ret, int retReg) {
			this.ret = ret;
			this.retReg = retReg;
		}
	}

	private static final Map<String, GType> IMPLICIT_UNIFORMS = new HashMap<String, GType>();
	static {
		IMPLICIT_UNIFORMS.put("iResolution", GType.VEC3);
		IMPLICIT_UNIFORMS.put("iTime", GType.FLOAT);
		IMPLICIT_UNIFORMS.put("iGlobalTime", GType.FLOAT);
		IMPLICIT_UNIFORMS.put("iTimeDelta", GType.FLOAT);
		IMPLICIT_UNIFORMS.put("iFrame", GType.INT);
		IMPLICIT_UNIFORMS.put("iFrameRate", GType.FLOAT);
		IMPLICIT_UNIFORMS.put("iSampleRate", GType.FLOAT);
		IMPLICIT_UNIFORMS.put("iMouse", GType.VEC4);
		IMPLICIT_UNIFORMS.put("iDate", GType.VEC4);
		IMPLICIT_UNIFORMS.put("iChannel0", GType.SAMPLER);
		IMPLICIT_UNIFORMS.put("iChannel1", GType.SAMPLER);
		IMPLICIT_UNIFORMS.put("iChannel2", GType.SAMPLER);
		IMPLICIT_UNIFORMS.put("iChannel3", GType.SAMPLER);
		IMPLICIT_UNIFORMS.put("iChannelResolution", GType.VEC3.array(4));
	}

	private final List<Op> ops = new ArrayList<Op>();
	private int nextReg = 0;
	private final Map<Integer, Float> initValues = new HashMap<Integer, Float>();
	private final Map<Float, Integer> constCache = new HashMap<Float, Integer>();
	private final Map<String, List<FuncDef>> funcs = new HashMap<String, List<FuncDef>>();
	private Map<String, GType> structs = new HashMap<String, GType>();
	private final Deque<Map<String, Var>> scopes = new ArrayDeque<Map<String, Var>>();
	private final List<Uniform> uniforms = new ArrayList<Uniform>();
	private int samplerCount = 0;
	private final Deque<LoopCtx> loops = new ArrayDeque<LoopCtx>();
	private final Deque<FuncCtx> fnStack = new ArrayDeque<FuncCtx>();
	private int inlineDepth = 0;
	private int fragCoordReg;
	private int outReg = -1;
	private String outVarName = null;

	// ------------------------------------------------------------------ entry

	Program compile(Unit unit) throws ShaderException {
		structs = unit.structs;
		for (FuncDef f : unit.functions) {
			List<FuncDef> list = funcs.get(f.name);
			if (list == null) {
				list = new ArrayList<FuncDef>();
				funcs.put(f.name, list);
			}
			for (FuncDef other : list) {
				if (sameSignature(other, f)) throw new ShaderException("function " + f.name + " is defined twice with the same parameters", f.line);
			}
			list.add(f);
		}
		scopes.push(new LinkedHashMap<String, Var>());
		fragCoordReg = alloc(4);
		define("gl_FragCoord", GType.VEC4, fragCoordReg, true);

		for (GlobalDecl g : unit.globals) {
			VarDecl d = g.decl;
			if (g.qualifier.equals("uniform")) {
				if (d.init != null) throw new ShaderException("uniforms cannot have initializers", d.line);
				declareUniform(d.name, d.type, d.line);
			} else if (g.qualifier.equals("out")) {
				if (outVarName != null) throw new ShaderException("only one output variable is supported", d.line);
				if (!d.type.sameShape(GType.VEC4)) throw new ShaderException("the output variable must be a vec4", d.line);
				outVarName = d.name;
				int reg = alloc(4);
				define(d.name, d.type, reg, false);
			} else if (g.qualifier.equals("in")) {
				// varyings make no sense for a fragment-only pipeline; treat as zero-initialised globals
				int reg = alloc(d.type.size());
				define(d.name, d.type, reg, false);
			} else {
				varDecl(d);
			}
		}

		if (funcs.containsKey("mainImage")) {
			FuncDef mi = funcs.get("mainImage").get(0);
			if (funcs.get("mainImage").size() > 1 || mi.params.size() != 2) throw new ShaderException("mainImage must be declared once as mainImage(out vec4 fragColor, in vec2 fragCoord)", mi.line);
			outReg = alloc(4);
			define("$fragColor", GType.VEC4, outReg, false);
			define("$fragCoord", GType.VEC2, fragCoordReg, true);
			List<Expr> args = new ArrayList<Expr>();
			args.add(new Ident("$fragColor"));
			args.add(new Ident("$fragCoord"));
			inlineCall(mi, args, null, mi.line);
		} else if (funcs.containsKey("main")) {
			FuncDef m = funcs.get("main").get(0);
			if (funcs.get("main").size() > 1 || !m.params.isEmpty()) throw new ShaderException("main() takes no parameters and must be defined once", m.line);
			if (outVarName != null) {
				outReg = lookup(outVarName, m.line).reg;
			} else {
				outReg = alloc(4);
				define("gl_FragColor", GType.VEC4, outReg, false);
			}
			inlineCall(m, new ArrayList<Expr>(), null, m.line);
		} else {
			throw new ShaderException("shader needs a mainImage(out vec4, in vec2) or main() function");
		}

		float[] init = new float[nextReg];
		for (Map.Entry<Integer, Float> e : initValues.entrySet()) init[e.getKey()] = e.getValue();
		return new Program(ops.toArray(new Op[0]), nextReg, init, uniforms, samplerCount, fragCoordReg, outReg);
	}

	// ------------------------------------------------------------------ registers and scopes

	private int alloc(int n) {
		int r = nextReg;
		nextReg += n;
		return r;
	}

	private Val constant(float v) {
		Integer reg = constCache.get(v);
		if (reg == null) {
			reg = alloc(1);
			initValues.put(reg, v);
			constCache.put(v, reg);
		}
		return new Val(GType.FLOAT, reg);
	}

	private Val zeros(GType type) {
		int reg = alloc(type.size());
		ops.add(new Const(reg, new float[type.size()]));
		return new Val(type, reg);
	}

	private void define(String name, GType type, int reg, boolean readOnly) {
		scopes.peek().put(name, new Var(type, reg, readOnly, -1));
	}

	private Var lookup(String name, int line) throws ShaderException {
		for (Map<String, Var> s : scopes) {
			Var v = s.get(name);
			if (v != null) return v;
		}
		GType implicit = IMPLICIT_UNIFORMS.get(name);
		if (implicit != null) {
			declareUniform(name, implicit, line);
			return lookup(name, line);
		}
		throw new ShaderException("unknown identifier '" + name + "'", line);
	}

	private void declareUniform(String name, GType type, int line) throws ShaderException {
		Map<String, Var> global = scopes.peekLast();
		if (global.containsKey(name)) throw new ShaderException("'" + name + "' is already declared", line);
		if (type.kind == Kind.SAMPLER) {
			int slot = samplerCount++;
			global.put(name, new Var(type, -1, true, slot));
			uniforms.add(new Uniform(name, type, -1, slot));
		} else {
			int reg = alloc(type.size());
			global.put(name, new Var(type, reg, true, -1));
			uniforms.add(new Uniform(name, type, reg, -1));
		}
	}

	// ------------------------------------------------------------------ statements

	private void statement(Stmt s) throws ShaderException {
		if (s instanceof Block) {
			scopes.push(new HashMap<String, Var>());
			for (Stmt c : ((Block) s).body) statement(c);
			scopes.pop();
		} else if (s instanceof VarDecl) {
			varDecl((VarDecl) s);
		} else if (s instanceof DeclGroup) {
			for (VarDecl d : ((DeclGroup) s).decls) varDecl(d);
		} else if (s instanceof ExprStmt) {
			expr(((ExprStmt) s).e);
		} else if (s instanceof If) {
			If i = (If) s;
			Val c = scalarBool(expr(i.cond), i.line);
			Jz jz = new Jz(c.reg, -1);
			ops.add(jz);
			statement(i.then);
			if (i.otherwise != null) {
				Jmp end = new Jmp(-1);
				ops.add(end);
				jz.target = ops.size();
				statement(i.otherwise);
				end.target = ops.size();
			} else {
				jz.target = ops.size();
			}
		} else if (s instanceof For) {
			For f = (For) s;
			scopes.push(new HashMap<String, Var>());
			if (f.init != null) statement(f.init);
			LoopCtx ctx = new LoopCtx();
			loops.push(ctx);
			int start = ops.size();
			Jz jz = null;
			if (f.cond != null) {
				Val c = scalarBool(expr(f.cond), f.line);
				jz = new Jz(c.reg, -1);
				ops.add(jz);
			}
			statement(f.body);
			int cont = ops.size();
			if (f.step != null) expr(f.step);
			ops.add(new Jmp(start));
			int end = ops.size();
			if (jz != null) jz.target = end;
			loops.pop();
			for (Jmp j : ctx.breaks) j.target = end;
			for (Jmp j : ctx.continues) j.target = cont;
			scopes.pop();
		} else if (s instanceof While) {
			While w = (While) s;
			LoopCtx ctx = new LoopCtx();
			loops.push(ctx);
			int start = ops.size();
			int cont;
			int end;
			if (w.doWhile) {
				statement(w.body);
				cont = ops.size();
				Val c = scalarBool(expr(w.cond), w.line);
				Jz jz = new Jz(c.reg, -1);
				ops.add(jz);
				ops.add(new Jmp(start));
				end = ops.size();
				jz.target = end;
			} else {
				cont = start;
				Val c = scalarBool(expr(w.cond), w.line);
				Jz jz = new Jz(c.reg, -1);
				ops.add(jz);
				statement(w.body);
				ops.add(new Jmp(start));
				end = ops.size();
				jz.target = end;
			}
			loops.pop();
			for (Jmp j : ctx.breaks) j.target = end;
			for (Jmp j : ctx.continues) j.target = cont;
		} else if (s instanceof Return) {
			Return r = (Return) s;
			if (fnStack.isEmpty()) throw new ShaderException("return outside a function", s.line);
			FuncCtx f = fnStack.peek();
			if (r.value != null) {
				if (f.ret.kind == Kind.VOID) throw new ShaderException("void function returns a value", s.line);
				Val v = convert(expr(r.value), f.ret, s.line);
				ops.add(new Mov(f.retReg, v.reg, f.ret.size()));
			} else if (f.ret.kind != Kind.VOID) {
				throw new ShaderException("return needs a value", s.line);
			}
			Jmp j = new Jmp(-1);
			ops.add(j);
			f.returns.add(j);
		} else if (s instanceof Break) {
			if (loops.isEmpty()) throw new ShaderException("break outside a loop", s.line);
			Jmp j = new Jmp(-1);
			ops.add(j);
			loops.peek().breaks.add(j);
		} else if (s instanceof Continue) {
			if (loops.isEmpty()) throw new ShaderException("continue outside a loop", s.line);
			Jmp j = new Jmp(-1);
			ops.add(j);
			loops.peek().continues.add(j);
		} else if (s instanceof Discard) {
			ops.add(new Ops.Discard());
		} else {
			throw new ShaderException("unsupported statement", s.line);
		}
	}

	private void varDecl(VarDecl d) throws ShaderException {
		if (d.type.kind == Kind.VOID) throw new ShaderException("variables cannot be void", d.line);
		if (d.type.kind == Kind.SAMPLER) throw new ShaderException("samplers must be uniforms", d.line);
		if (scopes.peek().containsKey(d.name)) throw new ShaderException("'" + d.name + "' is already declared in this scope", d.line);
		int reg = alloc(d.type.size());
		if (d.init != null) {
			Val v = convert(expr(d.init), d.type, d.line);
			ops.add(new Mov(reg, v.reg, d.type.size()));
		}
		define(d.name, d.type, reg, false);
	}

	// ------------------------------------------------------------------ expressions

	private Val expr(Expr e) throws ShaderException {
		if (e instanceof Literal) {
			Literal l = (Literal) e;
			Val c = constant(l.value);
			return new Val(l.type, c.reg);
		}
		if (e instanceof Ident) {
			Var v = lookup(((Ident) e).name, e.line);
			if (v.samplerSlot >= 0) return new Val(GType.SAMPLER, v.samplerSlot);
			return new Val(v.type, v.reg);
		}
		if (e instanceof Unary) {
			Unary u = (Unary) e;
			Val v = expr(u.e);
			if (u.op.equals("-")) {
				requireNumeric(v, e.line);
				return unary(Un.NEG, v);
			}
			if (u.op.equals("!")) {
				if (!v.type.isScalar()) throw new ShaderException("'!' needs a boolean", e.line);
				return new Val(GType.BOOL, unary(Un.NOT, v).reg);
			}
			throw new ShaderException("unsupported unary operator " + u.op, e.line);
		}
		if (e instanceof IncDec) {
			IncDec i = (IncDec) e;
			LVal l = lvalue(i.target);
			Val cur = load(l);
			int old = alloc(l.type.size());
			ops.add(new Mov(old, cur.reg, l.type.size()));
			Val one = constant(1f);
			Val res = binaryNumeric(i.increment ? Bin.ADD : Bin.SUB, cur, one, e.line);
			store(l, res, e.line);
			return i.prefix ? res : new Val(l.type, old);
		}
		if (e instanceof Binary) return binary((Binary) e);
		if (e instanceof Assign) {
			Assign a = (Assign) e;
			LVal l = lvalue(a.target);
			Val v = expr(a.value);
			if (!a.op.equals("=")) {
				Val cur = load(l);
				v = binaryNumeric(binKind(a.op.substring(0, 1), e.line), cur, v, e.line);
			}
			v = convert(v, l.type, e.line);
			store(l, v, e.line);
			return v;
		}
		if (e instanceof Ternary) {
			Ternary t = (Ternary) e;
			Val c = scalarBool(expr(t.cond), e.line);
			Jz jz = new Jz(c.reg, -1);
			ops.add(jz);
			int mark = ops.size();
			Val a = expr(t.a);
			int res = alloc(a.type.size());
			ops.add(new Mov(res, a.reg, a.type.size()));
			Jmp end = new Jmp(-1);
			ops.add(end);
			jz.target = ops.size();
			Val b = convert(expr(t.b), a.type, e.line);
			ops.add(new Mov(res, b.reg, a.type.size()));
			end.target = ops.size();
			return new Val(a.type, res);
		}
		if (e instanceof Call) return call((Call) e);
		if (e instanceof Member) return member((Member) e);
		if (e instanceof Index) return index((Index) e);
		throw new ShaderException("unsupported expression", e.line);
	}

	private Val member(Member m) throws ShaderException {
		Val base = expr(m.e);
		if (base.type.isStruct()) {
			int fi = base.type.def.field(m.name);
			if (fi < 0) throw new ShaderException("struct " + base.type.def.name + " has no field '" + m.name + "'", m.line);
			return new Val(base.type.def.fieldTypes[fi], base.reg + base.type.def.offsets[fi]);
		}
		if (base.type.isScalar() && m.name.length() == 1 && "xrs".indexOf(m.name.charAt(0)) >= 0) return base;
		if (!base.type.isVec()) throw new ShaderException("'." + m.name + "' applied to a " + base.type, m.line);
		int[] comps = swizzleComps(m.name, base.type.n, m.line);
		if (comps.length == base.type.n) {
			boolean identity = true;
			for (int i = 0; i < comps.length; i++) if (comps[i] != i) identity = false;
			if (identity) return base;
		}
		GType t = GType.vec(base.type.base, comps.length);
		int reg = alloc(comps.length);
		ops.add(new Swizzle(reg, base.reg, comps));
		return new Val(t, reg);
	}

	private static int[] swizzleComps(String name, int n, int line) throws ShaderException {
		if (name.length() < 1 || name.length() > 4) throw new ShaderException("bad swizzle ." + name, line);
		int[] comps = new int[name.length()];
		for (int i = 0; i < comps.length; i++) {
			int c = "xyzw".indexOf(name.charAt(i));
			if (c < 0) c = "rgba".indexOf(name.charAt(i));
			if (c < 0) c = "stpq".indexOf(name.charAt(i));
			if (c < 0 || c >= n) throw new ShaderException("bad swizzle ." + name + " on a " + n + "-component vector", line);
			comps[i] = c;
		}
		return comps;
	}

	private Val index(Index ix) throws ShaderException {
		Val base = expr(ix.e);
		Integer k = constantIndex(ix.index);
		if (base.type.isArray()) {
			GType elem = base.type.element();
			int es = elem.size();
			if (k != null) {
				if (k < 0 || k >= base.type.len) throw new ShaderException("array index " + k + " out of bounds", ix.line);
				return new Val(elem, base.reg + k * es);
			}
			Val i = expr(ix.index);
			int reg = alloc(es);
			ops.add(new LoadIdx(reg, base.reg, i.reg, es, base.type.len, 0, es));
			return new Val(elem, reg);
		}
		if (base.type.isVec()) {
			if (k != null) {
				if (k < 0 || k >= base.type.n) throw new ShaderException("vector index " + k + " out of bounds", ix.line);
				return new Val(GType.vec(base.type.base, 1), base.reg + k);
			}
			Val i = expr(ix.index);
			int reg = alloc(1);
			ops.add(new LoadIdx(reg, base.reg, i.reg, 1, base.type.n, 0, 1));
			return new Val(GType.vec(base.type.base, 1), reg);
		}
		if (base.type.isMat()) {
			int n = base.type.n;
			if (k != null) {
				if (k < 0 || k >= n) throw new ShaderException("matrix column " + k + " out of bounds", ix.line);
				return new Val(GType.floatVec(n), base.reg + k * n);
			}
			Val i = expr(ix.index);
			int reg = alloc(n);
			ops.add(new LoadIdx(reg, base.reg, i.reg, n, n, 0, n));
			return new Val(GType.floatVec(n), reg);
		}
		throw new ShaderException("cannot index a " + base.type, ix.line);
	}

	private static Integer constantIndex(Expr e) {
		if (e instanceof Literal) return (int) ((Literal) e).value;
		if (e instanceof Unary && ((Unary) e).op.equals("-") && ((Unary) e).e instanceof Literal) return -(int) ((Literal) ((Unary) e).e).value;
		return null;
	}

	// ------------------------------------------------------------------ l-values

	private LVal lvalue(Expr e) throws ShaderException {
		if (e instanceof Ident) {
			Var v = lookup(((Ident) e).name, e.line);
			if (v.readOnly || v.samplerSlot >= 0) throw new ShaderException("cannot assign to '" + ((Ident) e).name + "'", e.line);
			LVal l = new LVal();
			l.type = v.type;
			l.base = v.reg;
			return l;
		}
		if (e instanceof Member) {
			Member m = (Member) e;
			LVal inner = lvalue(m.e);
			if (inner.type.isStruct()) {
				int fi = inner.type.def.field(m.name);
				if (fi < 0) throw new ShaderException("struct " + inner.type.def.name + " has no field '" + m.name + "'", e.line);
				if (inner.comps != null) throw new ShaderException("bad field access", e.line);
				LVal l = new LVal();
				l.type = inner.type.def.fieldTypes[fi];
				if (inner.dynIdx >= 0) {
					l.base = inner.base;
					l.dynIdx = inner.dynIdx;
					l.elem = inner.elem;
					l.count = inner.count;
					l.dynOff = inner.dynOff + inner.type.def.offsets[fi];
				} else {
					l.base = inner.base + inner.type.def.offsets[fi];
				}
				return l;
			}
			if (inner.dynIdx >= 0) throw new ShaderException("swizzle on a dynamically indexed element is not supported here", e.line);
			if (!inner.type.isVec()) throw new ShaderException("'." + m.name + "' applied to a " + inner.type, e.line);
			int[] comps = swizzleComps(m.name, inner.type.n, e.line);
			for (int i = 0; i < comps.length; i++) for (int j = 0; j < i; j++) if (comps[i] == comps[j]) throw new ShaderException("duplicate component in assignment swizzle", e.line);
			if (inner.comps != null) {
				int[] mapped = new int[comps.length];
				for (int i = 0; i < comps.length; i++) mapped[i] = inner.comps[comps[i]];
				comps = mapped;
			}
			LVal l = new LVal();
			l.type = GType.vec(inner.type.base, comps.length);
			l.base = inner.base;
			l.comps = comps;
			return l;
		}
		if (e instanceof Index) {
			Index ix = (Index) e;
			LVal inner = lvalue(ix.e);
			if (inner.comps != null || inner.dynIdx >= 0) throw new ShaderException("unsupported nested indexing in assignment", e.line);
			Integer k = constantIndex(ix.index);
			int elem, count;
			GType elemType;
			if (inner.type.isArray()) {
				elemType = inner.type.element();
				elem = elemType.size();
				count = inner.type.len;
			} else if (inner.type.isVec()) {
				elemType = GType.vec(inner.type.base, 1);
				elem = 1;
				count = inner.type.n;
			} else if (inner.type.isMat()) {
				elemType = GType.floatVec(inner.type.n);
				elem = inner.type.n;
				count = inner.type.n;
			} else {
				throw new ShaderException("cannot index a " + inner.type, e.line);
			}
			LVal l = new LVal();
			l.type = elemType;
			if (k != null) {
				if (k < 0 || k >= count) throw new ShaderException("index " + k + " out of bounds", e.line);
				l.base = inner.base + k * elem;
			} else {
				l.base = inner.base;
				l.dynIdx = expr(ix.index).reg;
				l.elem = elem;
				l.count = count;
			}
			return l;
		}
		throw new ShaderException("expression is not assignable", e.line);
	}

	private Val load(LVal l) {
		if (l.comps != null) {
			int reg = alloc(l.comps.length);
			ops.add(new Swizzle(reg, l.base, l.comps));
			return new Val(l.type, reg);
		}
		if (l.dynIdx >= 0) {
			int reg = alloc(l.type.size());
			ops.add(new LoadIdx(reg, l.base, l.dynIdx, l.elem, l.count, l.dynOff, l.type.size()));
			return new Val(l.type, reg);
		}
		return new Val(l.type, l.base);
	}

	private void store(LVal l, Val v, int line) throws ShaderException {
		v = convert(v, l.type, line);
		if (l.comps != null) {
			ops.add(new Scatter(l.base, l.comps, v.reg));
		} else if (l.dynIdx >= 0) {
			ops.add(new StoreIdx(l.base, l.dynIdx, v.reg, l.elem, l.count, l.dynOff, l.type.size()));
		} else if (v.reg != l.base) {
			ops.add(new Mov(l.base, v.reg, l.type.size()));
		}
	}

	// ------------------------------------------------------------------ operators

	private static Bin binKind(String op, int line) throws ShaderException {
		switch (op) {
		case "+": return Bin.ADD;
		case "-": return Bin.SUB;
		case "*": return Bin.MUL;
		case "/": return Bin.DIV;
		case "%": return Bin.MOD;
		default: throw new ShaderException("unsupported operator " + op, line);
		}
	}

	private Val binary(Binary b) throws ShaderException {
		String op = b.op;
		if (op.equals(",")) {
			expr(b.l);
			return expr(b.r);
		}
		if (op.equals("&&") || op.equals("||") || op.equals("^^")) {
			Val l = scalarBool(expr(b.l), b.line);
			Val r = scalarBool(expr(b.r), b.line);
			int reg = alloc(1);
			ops.add(new Ops.Binary(op.equals("&&") ? Bin.AND : op.equals("||") ? Bin.OR : Bin.XOR, reg, l.reg, r.reg, 1, 1, 1));
			return new Val(GType.BOOL, reg);
		}
		Val l = expr(b.l);
		Val r = expr(b.r);
		if (op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=")) {
			if (!l.type.isScalar() || !r.type.isScalar()) throw new ShaderException("'" + op + "' compares scalars only; use lessThan() for vectors", b.line);
			Bin k = op.equals("<") ? Bin.LT : op.equals(">") ? Bin.GT : op.equals("<=") ? Bin.LE : Bin.GE;
			int reg = alloc(1);
			ops.add(new Ops.Binary(k, reg, l.reg, r.reg, 1, 1, 1));
			return new Val(GType.BOOL, reg);
		}
		if (op.equals("==") || op.equals("!=")) {
			requireNumeric(l, b.line);
			if (l.type.isScalar() && r.type.isScalar()) {
				int reg = alloc(1);
				ops.add(new Ops.Binary(op.equals("==") ? Bin.EQ : Bin.NE, reg, l.reg, r.reg, 1, 1, 1));
				return new Val(GType.BOOL, reg);
			}
			r = convert(r, l.type, b.line);
			int n = l.type.size();
			int tmp = alloc(n);
			ops.add(new Ops.Binary(op.equals("==") ? Bin.EQ : Bin.NE, tmp, l.reg, r.reg, n, 1, 1));
			int reg = alloc(1);
			ops.add(new Reduce(reg, tmp, n, op.equals("==")));
			return new Val(GType.BOOL, reg);
		}
		return binaryNumeric(binKind(op, b.line), l, r, b.line);
	}

	private void requireNumeric(Val v, int line) throws ShaderException {
		if (!v.type.isNumeric()) throw new ShaderException("expected a number, vector or matrix but got " + v.type, line);
	}

	private Val binaryNumeric(Bin kind, Val a, Val b, int line) throws ShaderException {
		requireNumeric(a, line);
		requireNumeric(b, line);
		GType ta = a.type, tb = b.type;
		if (kind == Bin.MUL && ta.isMat() && tb.isMat()) {
			if (ta.n != tb.n) throw new ShaderException("matrix size mismatch", line);
			int reg = alloc(ta.n * ta.n);
			ops.add(new MatMul(reg, a.reg, b.reg, ta.n, ta.n, ta.n));
			return new Val(ta, reg);
		}
		if (kind == Bin.MUL && ta.isMat() && tb.isVec()) {
			if (ta.n != tb.n) throw new ShaderException("mat" + ta.n + " * vec" + tb.n + " size mismatch", line);
			int reg = alloc(ta.n);
			ops.add(new MatMul(reg, a.reg, b.reg, ta.n, ta.n, 1));
			return new Val(GType.floatVec(ta.n), reg);
		}
		if (kind == Bin.MUL && ta.isVec() && tb.isMat()) {
			if (ta.n != tb.n) throw new ShaderException("vec" + ta.n + " * mat" + tb.n + " size mismatch", line);
			int reg = alloc(tb.n);
			ops.add(new MatMul(reg, a.reg, b.reg, 1, ta.n, tb.n));
			return new Val(GType.floatVec(tb.n), reg);
		}
		if (ta.isMat() && tb.isMat()) {
			if (ta.n != tb.n) throw new ShaderException("matrix size mismatch", line);
			int n = ta.n * ta.n;
			int reg = alloc(n);
			ops.add(new Ops.Binary(kind, reg, a.reg, b.reg, n, 1, 1));
			return new Val(ta, reg);
		}
		if (ta.isMat() || tb.isMat()) {
			GType mt = ta.isMat() ? ta : tb;
			Val other = ta.isMat() ? b : a;
			if (!other.type.isScalar()) throw new ShaderException("cannot combine " + ta + " and " + tb, line);
			int n = mt.n * mt.n;
			int reg = alloc(n);
			ops.add(new Ops.Binary(kind, reg, a.reg, b.reg, n, ta.isMat() ? 1 : 0, tb.isMat() ? 1 : 0));
			return new Val(mt, reg);
		}
		// scalars and vectors
		int n = Math.max(ta.n, tb.n);
		if (ta.n != 1 && tb.n != 1 && ta.n != tb.n) throw new ShaderException("cannot combine " + ta + " and " + tb, line);
		boolean ints = ta.componentKind() == Kind.INT && tb.componentKind() == Kind.INT;
		if (kind == Bin.DIV && ints) kind = Bin.IDIV;
		GType rt = GType.vec(ints ? Kind.INT : Kind.FLOAT, n);
		int reg = alloc(n);
		ops.add(new Ops.Binary(kind, reg, a.reg, b.reg, n, ta.n == 1 ? 0 : 1, tb.n == 1 ? 0 : 1));
		return new Val(rt, reg);
	}

	private Val unary(Un kind, Val v) {
		int n = v.type.size();
		int reg = alloc(n);
		ops.add(new Ops.Unary(kind, reg, v.reg, n));
		return new Val(v.type, reg);
	}

	private Val scalarBool(Val v, int line) throws ShaderException {
		if (!v.type.isScalar()) throw new ShaderException("condition must be a scalar, got " + v.type, line);
		return v;
	}

	/** Makes a value usable where target is expected: ints and floats share storage; scalars broadcast. */
	private Val convert(Val v, GType target, int line) throws ShaderException {
		if (v.type.kind == Kind.SAMPLER || target.kind == Kind.SAMPLER) {
			if (v.type.kind == target.kind) return v;
			throw new ShaderException("cannot convert " + v.type + " to " + target, line);
		}
		if (v.type.kind == Kind.STRUCT || target.kind == Kind.STRUCT) {
			if (v.type.def == target.def && v.type.len == target.len) return new Val(target, v.reg);
			throw new ShaderException("cannot convert " + v.type + " to " + target, line);
		}
		if (shapeCompatible(v.type, target)) {
			if (target.componentKind() == Kind.INT && v.type.componentKind() == Kind.FLOAT) return new Val(target, unary(Un.TRUNC, v).reg);
			return new Val(target, v.reg);
		}
		if (v.type.isScalar() && target.isVec()) {
			int reg = alloc(target.n);
			ops.add(new Swizzle(reg, v.reg, new int[target.n]));
			return new Val(target, reg);
		}
		if (v.type.isScalar() && target.isMat()) {
			return diagonal(v, target);
		}
		throw new ShaderException("cannot convert " + v.type + " to " + target, line);
	}

	/** Same storage layout: int/float/bool scalars (and vectors of them) are interchangeable. */
	static boolean shapeCompatible(GType a, GType b) {
		if (a.len != b.len) return false;
		if (a.isArray()) return shapeCompatible(a.element(), b.element());
		if (a.kind == Kind.STRUCT || b.kind == Kind.STRUCT) return a.def == b.def;
		if (a.isScalar() && b.isScalar()) return true;
		if (a.kind == Kind.VEC && b.kind == Kind.VEC) return a.n == b.n;
		if (a.kind == Kind.MAT && b.kind == Kind.MAT) return a.n == b.n;
		return false;
	}

	private Val diagonal(Val s, GType mt) {
		int n = mt.n;
		Val z = zeros(mt);
		for (int i = 0; i < n; i++) ops.add(new Mov(z.reg + i * n + i, s.reg, 1));
		return new Val(mt, z.reg);
	}

	// ------------------------------------------------------------------ calls

	private Val call(Call c) throws ShaderException {
		GType ctor = GType.parse(c.name);
		if (ctor == null) ctor = structs.get(c.name);
		if (ctor != null && !funcs.containsKey(c.name)) return construct(ctor, c);
		List<FuncDef> candidates = funcs.get(c.name);
		if (candidates != null) {
			Val[] vals = new Val[c.args.size()];
			for (int i = 0; i < vals.length; i++) vals[i] = expr(c.args.get(i));
			FuncDef f = resolve(c.name, candidates, vals, c.line);
			return inlineCall(f, c.args, vals, c.line);
		}
		return builtin(c);
	}

	private static boolean sameSignature(FuncDef a, FuncDef b) {
		if (a.params.size() != b.params.size()) return false;
		for (int i = 0; i < a.params.size(); i++) if (!a.params.get(i).type.sameShape(b.params.get(i).type)) return false;
		return true;
	}

	/** Picks the overload whose parameters match the argument types best (exact beats int/float mixing). */
	private static FuncDef resolve(String name, List<FuncDef> candidates, Val[] vals, int line) throws ShaderException {
		FuncDef best = null;
		int bestScore = Integer.MAX_VALUE;
		boolean ambiguous = false;
		for (FuncDef f : candidates) {
			if (f.params.size() != vals.length) continue;
			int score = 0;
			boolean ok = true;
			for (int i = 0; i < vals.length && ok; i++) {
				GType pt = f.params.get(i).type, at = vals[i].type;
				if (pt.kind == Kind.SAMPLER || at.kind == Kind.SAMPLER) {
					ok = pt.kind == at.kind;
				} else if (pt.sameShape(at)) {
					if (pt.componentKind() != at.componentKind()) score += 1;
				} else if (shapeCompatible(pt, at)) {
					score += 1;
				} else if (at.isScalar() && !f.params.get(i).qualifier.equals("in") == false && (pt.isVec() || pt.isMat())) {
					score += 4; // scalar broadcast into a vector parameter
				} else {
					ok = false;
				}
			}
			if (!ok) continue;
			if (score < bestScore) {
				best = f;
				bestScore = score;
				ambiguous = false;
			} else if (score == bestScore) {
				ambiguous = true;
			}
		}
		if (best == null) {
			StringBuilder sb = new StringBuilder();
			for (Val v : vals) {
				if (sb.length() > 0) sb.append(", ");
				sb.append(v.type);
			}
			throw new ShaderException("no matching overload for " + name + "(" + sb + ")", line);
		}
		if (ambiguous) throw new ShaderException("ambiguous call to " + name + "; add explicit conversions", line);
		return best;
	}

	private Val construct(GType t, Call c) throws ShaderException {
		if (c.args.isEmpty()) throw new ShaderException(t + "() needs arguments", c.line);
		if (t.isStruct()) {
			GType.StructDef def = t.def;
			if (c.args.size() != def.fieldTypes.length) throw new ShaderException(def.name + "() expects " + def.fieldTypes.length + " values (one per field), got " + c.args.size(), c.line);
			int reg = alloc(def.size);
			for (int i = 0; i < def.fieldTypes.length; i++) {
				Val v = convert(expr(c.args.get(i)), def.fieldTypes[i], c.line);
				ops.add(new Mov(reg + def.offsets[i], v.reg, def.fieldTypes[i].size()));
			}
			return new Val(t, reg);
		}
		List<Val> args = new ArrayList<Val>();
		int total = 0;
		for (Expr a : c.args) {
			Val v = expr(a);
			requireNumeric(v, c.line);
			args.add(v);
			total += v.type.size();
		}
		if (t.isScalar()) {
			Val first = args.get(0);
			Val comp = new Val(GType.FLOAT, first.reg);
			if (t.kind == Kind.INT) return new Val(GType.INT, unary(Un.TRUNC, comp).reg);
			if (t.kind == Kind.BOOL) return new Val(GType.BOOL, unary(Un.TOBOOL, comp).reg);
			return new Val(GType.FLOAT, first.reg);
		}
		if (t.isVec()) {
			int n = t.n;
			if (args.size() == 1 && args.get(0).type.isScalar()) {
				int reg = alloc(n);
				ops.add(new Swizzle(reg, args.get(0).reg, new int[n]));
				return new Val(t, reg);
			}
			if (total < n) throw new ShaderException("not enough components for " + t + " (" + total + " given)", c.line);
			int reg = alloc(n);
			int filled = 0;
			for (Val v : args) {
				int take = Math.min(v.type.size(), n - filled);
				if (take <= 0) break;
				ops.add(new Mov(reg + filled, v.reg, take));
				filled += take;
			}
			Val out = new Val(t, reg);
			if (t.base == Kind.INT && args.get(0).type.componentKind() != Kind.INT) out = new Val(t, unary(Un.TRUNC, new Val(GType.floatVec(n), reg)).reg);
			return out;
		}
		if (t.isMat()) {
			int n = t.n;
			if (args.size() == 1 && args.get(0).type.isScalar()) return diagonal(args.get(0), t);
			if (args.size() == 1 && args.get(0).type.isMat()) {
				Val m = args.get(0);
				Val z = diagonal(constant(1f), t);
				int copy = Math.min(n, m.type.n);
				for (int col = 0; col < copy; col++) ops.add(new Mov(z.reg + col * n, m.reg + col * m.type.n, copy));
				return z;
			}
			if (total < n * n) throw new ShaderException("not enough components for " + t + " (" + total + " given)", c.line);
			int reg = alloc(n * n);
			int filled = 0;
			for (Val v : args) {
				int take = Math.min(v.type.size(), n * n - filled);
				if (take <= 0) break;
				ops.add(new Mov(reg + filled, v.reg, take));
				filled += take;
			}
			return new Val(t, reg);
		}
		throw new ShaderException("cannot construct " + t, c.line);
	}

	/**
	 * Inlines a call. pre holds the already-evaluated argument values (null: evaluate here);
	 * out/inout parameters are resolved as l-values from the expressions either way.
	 */
	private Val inlineCall(FuncDef f, List<Expr> args, Val[] pre, int line) throws ShaderException {
		if (args.size() != f.params.size()) throw new ShaderException(f.name + " expects " + f.params.size() + " arguments, got " + args.size(), line);
		if (++inlineDepth > MAX_INLINE_DEPTH) throw new ShaderException("call nesting too deep (recursion is not allowed): " + f.name, line);
		Map<String, Var> scope = new HashMap<String, Var>();
		List<LVal> outTargets = new ArrayList<LVal>();
		List<Integer> outTemps = new ArrayList<Integer>();
		List<GType> outTypes = new ArrayList<GType>();
		for (int i = 0; i < args.size(); i++) {
			Param p = f.params.get(i);
			Expr a = args.get(i);
			if (p.qualifier.equals("out") || p.qualifier.equals("inout")) {
				LVal l = lvalue(a);
				if (!shapeCompatible(l.type, p.type)) throw new ShaderException("argument " + (i + 1) + " of " + f.name + ": expected " + p.type + ", got " + l.type, line);
				if (l.plain()) {
					scope.put(p.name, new Var(p.type, l.base, false, -1));
				} else {
					int tmp = alloc(p.type.size());
					if (p.qualifier.equals("inout")) {
						Val cur = load(l);
						ops.add(new Mov(tmp, cur.reg, p.type.size()));
					}
					scope.put(p.name, new Var(p.type, tmp, false, -1));
					outTargets.add(l);
					outTemps.add(tmp);
					outTypes.add(p.type);
				}
			} else {
				Val v = pre != null && pre[i] != null ? pre[i] : expr(a);
				if (p.type.kind == Kind.SAMPLER) {
					if (v.type.kind != Kind.SAMPLER) throw new ShaderException("argument " + (i + 1) + " of " + f.name + " must be a sampler2D", line);
					scope.put(p.name, new Var(p.type, -1, true, v.reg));
					continue;
				}
				v = convert(v, p.type, line);
				int reg = alloc(p.type.size());
				ops.add(new Mov(reg, v.reg, p.type.size()));
				scope.put(p.name, new Var(p.type, reg, false, -1));
			}
		}
		int retReg = f.ret.kind == Kind.VOID ? -1 : alloc(f.ret.size());
		FuncCtx ctx = new FuncCtx(f.ret, retReg);
		fnStack.push(ctx);
		// Function bodies only see globals and their own parameters.
		Deque<Map<String, Var>> saved = new ArrayDeque<Map<String, Var>>(scopes);
		Map<String, Var> global = scopes.peekLast();
		scopes.clear();
		scopes.push(global);
		scopes.push(scope);
		Deque<LoopCtx> savedLoops = new ArrayDeque<LoopCtx>(loops);
		loops.clear();
		try {
			for (Stmt s : f.body.body) statement(s);
		} finally {
			scopes.clear();
			for (Map<String, Var> m : saved) scopes.addLast(m);
			loops.clear();
			for (LoopCtx l : savedLoops) loops.addLast(l);
			fnStack.pop();
			inlineDepth--;
		}
		for (Jmp j : ctx.returns) j.target = ops.size();
		for (int i = 0; i < outTargets.size(); i++) store(outTargets.get(i), new Val(outTypes.get(i), outTemps.get(i)), line);
		return retReg < 0 ? new Val(GType.VOID, -1) : new Val(f.ret, retReg);
	}

	// ------------------------------------------------------------------ built-ins

	private Val builtin(Call c) throws ShaderException {
		String name = c.name;
		List<Val> a = new ArrayList<Val>();
		for (Expr e : c.args) a.add(expr(e));
		int argc = a.size();
		switch (name) {
		case "radians": return un(Un.RADIANS, a, c);
		case "degrees": return un(Un.DEGREES, a, c);
		case "sin": return un(Un.SIN, a, c);
		case "cos": return un(Un.COS, a, c);
		case "tan": return un(Un.TAN, a, c);
		case "asin": return un(Un.ASIN, a, c);
		case "acos": return un(Un.ACOS, a, c);
		case "sinh": return un(Un.SINH, a, c);
		case "cosh": return un(Un.COSH, a, c);
		case "tanh": return un(Un.TANH, a, c);
		case "exp": return un(Un.EXP, a, c);
		case "log": return un(Un.LOG, a, c);
		case "exp2": return un(Un.EXP2, a, c);
		case "log2": return un(Un.LOG2, a, c);
		case "sqrt": return un(Un.SQRT, a, c);
		case "inversesqrt": return un(Un.INVSQRT, a, c);
		case "abs": return un(Un.ABS, a, c);
		case "sign": return un(Un.SIGN, a, c);
		case "floor": return un(Un.FLOOR, a, c);
		case "ceil": return un(Un.CEIL, a, c);
		case "round": case "roundEven": return un(Un.ROUND, a, c);
		case "trunc": return un(Un.TRUNC, a, c);
		case "fract": return un(Un.FRACT, a, c);
		case "not": return un(Un.NOT, a, c);
		case "atan":
			if (argc == 1) return un(Un.ATAN, a, c);
			return bin(Bin.ATAN2, a, c);
		case "pow": return bin(Bin.POW, a, c);
		case "mod": return bin(Bin.MOD, a, c);
		case "min": return bin(Bin.MIN, a, c);
		case "max": return bin(Bin.MAX, a, c);
		case "step": return bin(Bin.STEP, a, c);
		case "lessThan": return compare(Bin.LT, a, c);
		case "lessThanEqual": return compare(Bin.LE, a, c);
		case "greaterThan": return compare(Bin.GT, a, c);
		case "greaterThanEqual": return compare(Bin.GE, a, c);
		case "equal": return compare(Bin.EQ, a, c);
		case "notEqual": return compare(Bin.NE, a, c);
		case "clamp": return tern(Tern.CLAMP, a, c);
		case "mix": return tern(Tern.MIX, a, c);
		case "smoothstep": return tern(Tern.SMOOTHSTEP, a, c);
		case "any": case "all": {
			need(a, 1, c);
			int reg = alloc(1);
			ops.add(new Reduce(reg, a.get(0).reg, a.get(0).type.size(), name.equals("all")));
			return new Val(GType.BOOL, reg);
		}
		case "length": {
			need(a, 1, c);
			int reg = alloc(1);
			ops.add(new Length(reg, a.get(0).reg, a.get(0).type.size()));
			return new Val(GType.FLOAT, reg);
		}
		case "distance": {
			need(a, 2, c);
			Val y = convert(a.get(1), a.get(0).type, c.line);
			int reg = alloc(1);
			ops.add(new Distance(reg, a.get(0).reg, y.reg, a.get(0).type.size()));
			return new Val(GType.FLOAT, reg);
		}
		case "dot": {
			need(a, 2, c);
			Val y = convert(a.get(1), a.get(0).type, c.line);
			int reg = alloc(1);
			ops.add(new Dot(reg, a.get(0).reg, y.reg, a.get(0).type.size()));
			return new Val(GType.FLOAT, reg);
		}
		case "cross": {
			need(a, 2, c);
			if (a.get(0).type.size() != 3 || a.get(1).type.size() != 3) throw new ShaderException("cross() needs two vec3", c.line);
			int reg = alloc(3);
			ops.add(new Cross(reg, a.get(0).reg, a.get(1).reg));
			return new Val(GType.VEC3, reg);
		}
		case "normalize": {
			need(a, 1, c);
			int n = a.get(0).type.size();
			int reg = alloc(n);
			ops.add(new Normalize(reg, a.get(0).reg, n));
			return new Val(a.get(0).type, reg);
		}
		case "reflect": {
			need(a, 2, c);
			int n = a.get(0).type.size();
			Val nrm = convert(a.get(1), a.get(0).type, c.line);
			int reg = alloc(n);
			ops.add(new Reflect(reg, a.get(0).reg, nrm.reg, n));
			return new Val(a.get(0).type, reg);
		}
		case "refract": {
			need(a, 3, c);
			int n = a.get(0).type.size();
			Val nrm = convert(a.get(1), a.get(0).type, c.line);
			int reg = alloc(n);
			ops.add(new Refract(reg, a.get(0).reg, nrm.reg, a.get(2).reg, n));
			return new Val(a.get(0).type, reg);
		}
		case "faceforward": {
			need(a, 3, c);
			Val nn = a.get(0);
			int n = nn.type.size();
			Val i = convert(a.get(1), nn.type, c.line), nref = convert(a.get(2), nn.type, c.line);
			int d = alloc(1);
			ops.add(new Dot(d, nref.reg, i.reg, n));
			int lt = alloc(1);
			ops.add(new Ops.Binary(Bin.LT, lt, d, constant(0f).reg, 1, 1, 1));
			Val neg = unary(Un.NEG, nn);
			int reg = alloc(n);
			ops.add(new Ops.Ternary(Tern.MIX, reg, neg.reg, nn.reg, lt, n, 1, 1, 0));
			return new Val(nn.type, reg);
		}
		case "matrixCompMult": {
			need(a, 2, c);
			int n = a.get(0).type.size();
			int reg = alloc(n);
			ops.add(new Ops.Binary(Bin.MUL, reg, a.get(0).reg, a.get(1).reg, n, 1, 1));
			return new Val(a.get(0).type, reg);
		}
		case "transpose": {
			need(a, 1, c);
			if (!a.get(0).type.isMat()) throw new ShaderException("transpose() needs a matrix", c.line);
			int n = a.get(0).type.n;
			int reg = alloc(n * n);
			ops.add(new Transpose(reg, a.get(0).reg, n));
			return new Val(a.get(0).type, reg);
		}
		case "texture": case "texture2D": case "textureLod": case "texture2DLod": case "textureGrad": {
			if (argc < 2) throw new ShaderException(name + "(sampler, uv) expected", c.line);
			if (a.get(0).type.kind != Kind.SAMPLER) throw new ShaderException(name + ": first argument must be a sampler2D", c.line);
			Val uv = a.get(1);
			if (uv.type.size() < 2) throw new ShaderException(name + ": coordinates must be a vec2", c.line);
			int reg = alloc(4);
			ops.add(new Tex(reg, a.get(0).reg, uv.reg));
			return new Val(GType.VEC4, reg);
		}
		case "textureSize": {
			need(a, 2, c);
			return zeros(GType.vec(Kind.INT, 2));
		}
		case "dFdx": case "dFdy": case "fwidth": {
			need(a, 1, c);
			return zeros(a.get(0).type);
		}
		default:
			throw new ShaderException("unknown function '" + name + "'", c.line);
		}
	}

	private static void need(List<Val> a, int n, Call c) throws ShaderException {
		if (a.size() != n) throw new ShaderException(c.name + "() expects " + n + " argument" + (n == 1 ? "" : "s") + ", got " + a.size(), c.line);
	}

	private Val un(Un kind, List<Val> a, Call c) throws ShaderException {
		need(a, 1, c);
		requireNumeric(a.get(0), c.line);
		Val v = a.get(0);
		Val r = unary(kind, v);
		return kind == Un.NOT ? new Val(GType.vec(Kind.BOOL, v.type.size()), r.reg) : new Val(GType.vec(Kind.FLOAT, v.type.size()), r.reg);
	}

	/** Component-wise binary built-in: the vector argument decides the size, scalars broadcast. */
	private Val bin(Bin kind, List<Val> a, Call c) throws ShaderException {
		need(a, 2, c);
		Val x = a.get(0), y = a.get(1);
		requireNumeric(x, c.line);
		requireNumeric(y, c.line);
		int n = Math.max(x.type.size(), y.type.size());
		if (x.type.size() != 1 && y.type.size() != 1 && x.type.size() != y.type.size()) throw new ShaderException(c.name + "(): size mismatch " + x.type + " vs " + y.type, c.line);
		int reg = alloc(n);
		ops.add(new Ops.Binary(kind, reg, x.reg, y.reg, n, x.type.size() == 1 ? 0 : 1, y.type.size() == 1 ? 0 : 1));
		return new Val(GType.vec(Kind.FLOAT, n), reg);
	}

	private Val compare(Bin kind, List<Val> a, Call c) throws ShaderException {
		Val r = bin(kind, a, c);
		return new Val(GType.vec(Kind.BOOL, r.type.size()), r.reg);
	}

	private Val tern(Tern kind, List<Val> a, Call c) throws ShaderException {
		need(a, 3, c);
		Val x = a.get(0), y = a.get(1), z = a.get(2);
		requireNumeric(x, c.line);
		requireNumeric(y, c.line);
		requireNumeric(z, c.line);
		int n = Math.max(x.type.size(), Math.max(y.type.size(), z.type.size()));
		for (Val v : a) if (v.type.size() != 1 && v.type.size() != n) throw new ShaderException(c.name + "(): size mismatch", c.line);
		int reg = alloc(n);
		ops.add(new Ops.Ternary(kind, reg, x.reg, y.reg, z.reg, n, x.type.size() == 1 ? 0 : 1, y.type.size() == 1 ? 0 : 1, z.type.size() == 1 ? 0 : 1));
		return new Val(GType.vec(Kind.FLOAT, n), reg);
	}
}
