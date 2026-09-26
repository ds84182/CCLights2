package ds.mods.CCLights2.gpu.shader;

/**
 * GLSL types of the supported subset. Everything is stored as floats in the register file:
 * scalars use one register, vecN uses N, matN uses N*N (column major), arrays are contiguous.
 */
public final class GType {
	public enum Kind { VOID, BOOL, INT, FLOAT, VEC, MAT, SAMPLER, STRUCT }

	/** Layout of a user struct: fields are stored back to back. */
	public static final class StructDef {
		public final String name;
		public final String[] fieldNames;
		public final GType[] fieldTypes;
		public final int[] offsets;
		public final int size;

		public StructDef(String name, String[] fieldNames, GType[] fieldTypes) {
			this.name = name;
			this.fieldNames = fieldNames;
			this.fieldTypes = fieldTypes;
			this.offsets = new int[fieldTypes.length];
			int off = 0;
			for (int i = 0; i < fieldTypes.length; i++) {
				offsets[i] = off;
				off += fieldTypes[i].size();
			}
			this.size = off;
		}

		public int field(String n) {
			for (int i = 0; i < fieldNames.length; i++) if (fieldNames[i].equals(n)) return i;
			return -1;
		}
	}

	public final Kind kind;
	/** For VEC: the component type (FLOAT, INT or BOOL). */
	public final Kind base;
	/** Component count for VEC, dimension for MAT, 1 otherwise. */
	public final int n;
	/** Array length, or 0 when not an array. */
	public final int len;
	/** Layout for STRUCT types, null otherwise. */
	public final StructDef def;

	private GType(Kind kind, Kind base, int n, int len) {
		this(kind, base, n, len, null);
	}

	private GType(Kind kind, Kind base, int n, int len, StructDef def) {
		this.kind = kind;
		this.base = base;
		this.n = n;
		this.len = len;
		this.def = def;
	}

	public static GType struct(StructDef def) {
		return new GType(Kind.STRUCT, Kind.STRUCT, 1, 0, def);
	}

	public boolean isStruct() {
		return len == 0 && kind == Kind.STRUCT;
	}

	public static final GType VOID = new GType(Kind.VOID, Kind.VOID, 0, 0);
	public static final GType BOOL = new GType(Kind.BOOL, Kind.BOOL, 1, 0);
	public static final GType INT = new GType(Kind.INT, Kind.INT, 1, 0);
	public static final GType FLOAT = new GType(Kind.FLOAT, Kind.FLOAT, 1, 0);
	public static final GType SAMPLER = new GType(Kind.SAMPLER, Kind.SAMPLER, 1, 0);
	public static final GType VEC2 = new GType(Kind.VEC, Kind.FLOAT, 2, 0);
	public static final GType VEC3 = new GType(Kind.VEC, Kind.FLOAT, 3, 0);
	public static final GType VEC4 = new GType(Kind.VEC, Kind.FLOAT, 4, 0);
	public static final GType MAT2 = new GType(Kind.MAT, Kind.FLOAT, 2, 0);
	public static final GType MAT3 = new GType(Kind.MAT, Kind.FLOAT, 3, 0);
	public static final GType MAT4 = new GType(Kind.MAT, Kind.FLOAT, 4, 0);

	public static GType vec(Kind base, int n) {
		if (n == 1) return base == Kind.INT ? INT : base == Kind.BOOL ? BOOL : FLOAT;
		return new GType(Kind.VEC, base, n, 0);
	}

	public static GType floatVec(int n) {
		return vec(Kind.FLOAT, n);
	}

	public static GType mat(int n) {
		return n == 2 ? MAT2 : n == 3 ? MAT3 : MAT4;
	}

	public GType array(int length) {
		return new GType(kind, base, n, length, def);
	}

	public GType element() {
		return new GType(kind, base, n, 0, def);
	}

	public boolean isArray() {
		return len > 0;
	}

	public boolean isScalar() {
		return len == 0 && (kind == Kind.FLOAT || kind == Kind.INT || kind == Kind.BOOL);
	}

	public boolean isVec() {
		return len == 0 && kind == Kind.VEC;
	}

	public boolean isMat() {
		return len == 0 && kind == Kind.MAT;
	}

	public boolean isNumeric() {
		return isScalar() || isVec() || isMat();
	}

	/** Component type: the scalar kind of scalars and vectors, FLOAT for matrices. */
	public Kind componentKind() {
		if (kind == Kind.VEC) return base;
		if (kind == Kind.MAT) return Kind.FLOAT;
		return kind;
	}

	/** Number of registers a value of this type occupies. */
	public int size() {
		int s = elemSize();
		return len > 0 ? s * len : s;
	}

	/** Number of registers of one element (1 for scalars, n for vecs, n*n for mats, the struct size for structs). */
	public int elemSize() {
		if (kind == Kind.STRUCT) return def.size;
		return kind == Kind.MAT ? n * n : n;
	}

	public boolean sameShape(GType o) {
		return kind == o.kind && n == o.n && len == o.len && def == o.def;
	}

	/** Parses a type keyword; returns null when the word is not a type. */
	public static GType parse(String word) {
		switch (word) {
		case "void": return VOID;
		case "bool": return BOOL;
		case "int": case "uint": return INT;
		case "float": case "double": return FLOAT;
		case "vec2": return VEC2;
		case "vec3": return VEC3;
		case "vec4": return VEC4;
		case "ivec2": return vec(Kind.INT, 2);
		case "ivec3": return vec(Kind.INT, 3);
		case "ivec4": return vec(Kind.INT, 4);
		case "bvec2": return vec(Kind.BOOL, 2);
		case "bvec3": return vec(Kind.BOOL, 3);
		case "bvec4": return vec(Kind.BOOL, 4);
		case "mat2": return MAT2;
		case "mat3": return MAT3;
		case "mat4": return MAT4;
		case "sampler2D": return SAMPLER;
		default: return null;
		}
	}

	@Override
	public String toString() {
		String s;
		switch (kind) {
		case VEC: s = (base == Kind.INT ? "ivec" : base == Kind.BOOL ? "bvec" : "vec") + n; break;
		case MAT: s = "mat" + n; break;
		case SAMPLER: s = "sampler2D"; break;
		case STRUCT: s = def.name; break;
		default: s = kind.name().toLowerCase(); break;
		}
		return len > 0 ? s + "[" + len + "]" : s;
	}
}
