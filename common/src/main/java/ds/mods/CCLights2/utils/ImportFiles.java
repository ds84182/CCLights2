package ds.mods.CCLights2.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import dan200.computercraft.api.lua.LuaException;

/**
 * Reads the files named in {@code gpu.import("file")} from a computer's save folder
 * ({@code <world>/computercraft/computer/<id>}). Minecraft-free so the path checks are testable.
 */
public final class ImportFiles {
	/** Largest file gpu.import() reads. */
	public static final long MAX_FILE_SIZE = 16L * 1024 * 1024;

	private ImportFiles() {}

	/** {@code <worldRoot>/computercraft/computer/<id>}, where CC: Tweaked keeps a computer's files. */
	public static Path computerDir(Path worldRoot, int computerId) {
		return worldRoot.resolve("computercraft").resolve("computer").resolve(Integer.toString(computerId));
	}

	/**
	 * Reads {@code file} relative to {@code dir}, refusing anything that resolves outside it.
	 * @param method Lua method name, used in error messages
	 */
	public static byte[] read(Path dir, String method, String file) throws LuaException {
		Path base = dir.toAbsolutePath().normalize();
		Path f;
		try {
			f = base.resolve(file).toAbsolutePath().normalize();
		} catch (InvalidPathException e) {
			throw new LuaException(method + ": invalid file name '" + file + "'");
		}
		if (!f.startsWith(base) || f.equals(base)) throw new LuaException(method + ": invalid file name '" + file + "'");
		if (!Files.isRegularFile(f)) throw new LuaException(method + ": no such file '" + file + "' in the computer's folder");
		try {
			if (Files.size(f) > MAX_FILE_SIZE) throw new LuaException(method + ": file too large (limit " + (MAX_FILE_SIZE >> 20) + " MB)");
			return Files.readAllBytes(f);
		} catch (IOException e) {
			throw new LuaException(method + ": " + e.getMessage());
		}
	}
}
