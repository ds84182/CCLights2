package ds.mods.CCLights2.block.tileentity;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.ForgeDirection;

import org.apache.commons.lang3.ArrayUtils;

import com.google.common.collect.ImmutableSortedSet;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.Player;
import cpw.mods.fml.relauncher.Side;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import ds.mods.CCLights2.CCLights2;
import static ds.mods.CCLights2.utils.TypeConverters.*;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.network.PacketSenders;

public class TileEntityGPU extends TileEntity implements IPeripheral {
	public GPU gpu;
	private ArrayList<DrawCMD> newarr = new ArrayList<DrawCMD>();
	public ArrayList<IComputerAccess> comp = new ArrayList<IComputerAccess>();
	private TreeMap<String, Integer> playerToClickMap = new TreeMap<String, Integer>();
	private TreeMap<Integer, int[]> clickToDataMap = new TreeMap<Integer, int[]>();
	public int[] addedType = new int[1025];
	private boolean frame = false;
	private byte ticks = 0;
	private boolean sentOnce = false;

	public TileEntityGPU() {
		gpu = new GPU(1024 * 8);
		gpu.tile = this;
	}

	public void startClick(Player player, int button, int x, int y) {
		int id = new Random().nextInt();
		while (playerToClickMap.containsValue(id)) {
			id = new Random().nextInt();
		}
		playerToClickMap.put(((EntityPlayer) player).username, id);
		clickToDataMap.put(id, new int[] { button, x, y });

		String event = "monitor_down";
		Object[] args = new Object[] { button, x, y, id };
		for (IComputerAccess c : comp) {
			c.queueEvent(event, args);
		}
	}

	public void moveClick(Player player, int nx, int ny) {
		int id = playerToClickMap.get(((EntityPlayer) player).username);
		int[] data = clickToDataMap.get(id);
		int button = data[0];
		data[1] = nx;
		data[2] = ny;

		String event = "monitor_move";
		Object[] args = new Object[] { button, nx, ny, id };
		for (IComputerAccess c : comp) {
			c.queueEvent(event, args);
		}
	}

	public void endClick(Player player) {
		int id = playerToClickMap.get(((EntityPlayer) player).username);
		int[] data = clickToDataMap.get(id);
		int button = data[0];
		int x = data[1];
		int y = data[2];

		String event = "monitor_up";
		Object[] args = new Object[] { button, x, y, id };
		for (IComputerAccess c : comp) {
			c.queueEvent(event, args);
		}
		playerToClickMap.remove(((EntityPlayer) player).username);
		clickToDataMap.remove(id);
	}

	@Override
	public String getType() {
		return "GPU";
	}

	@Override
	public String[] getMethodNames() {
		return new String[] { "fill", "createTexture", "getFreeMemory",
				"getTotalMemory", "getUsedMemory", "bindTexture", "plot",
				"drawTexture", "freeTexture", "line", "getSize", "getPixels",
				"rectangle", "filledRectangle", "getBindedTexture",
				"setPixelsRaw", "setPixelsRawYX", "flipTextureV", "import",
				"export", "drawText", "getTextWidth", "setColor", "getColor",
				"translate", "rotate", "rotateAround", "scale", "push", "pop",
				"getMonitor", "blur", "startFrame", "endFrame", "clearRect",
				"origin" };
	}

	@SuppressWarnings("rawtypes")
	@Override
	public synchronized Object[] callMethod(IComputerAccess computer,
			ILuaContext context, int method, Object[] args) throws Exception {
		switch (method) {
		case 0: {
			// fill
			DrawCMD cmd = new DrawCMD();
			cmd.cmd = 0;
			gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 1: {
			// createTexture
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[] {
					checkInteger(args, 0, "createTexture"),
					checkInteger(args, 1, "createTexture"), };
			cmd.cmd = 6;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			int id = (Integer) ret[0];
			if (id == -1) {
				throw new Exception("createTexture: Not enough memory");
			} else if (id == -2) {
				throw new Exception("createTexture: Not enough texture slots");
			} else {
				gpu.drawlist.push(cmd);
				return ret;
			}
		}
		case 2: {
			// getFreeMemory
			return new Object[] { gpu.getFreeMemory() };
		}
		case 3: {
			// getTotalMemory
			return new Object[] { gpu.maxmem };
		}
		case 4: {
			// getUsedMemory
			return new Object[] { gpu.getUsedMemory() };
		}
		case 5: {
			// bindTexture
			int texid = checkInteger(args, 0, "bindTexture");
			if (gpu.textures[texid] == null)
				throw new Exception("Texture does not exist");
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[] { texid };
			cmd.cmd = 7;
			cmd.args = nargs;
			gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 6: {
			// was plot and setColorRGB is now plot
			int x = checkInteger(args, 0, "plot");
			int y = checkInteger(args, 1, "plot");
			Point2D point = gpu.transform.transform(new Point2D.Double(x, y),
					null);
			double tx = point.getX();
			double ty = point.getY();
			int w = gpu.bindedTexture.getWidth();
			int h = gpu.bindedTexture.getHeight();
			if (tx < 0 || ty < 0 || tx > w || ty > h) // Don't draw if out of
														// bounds!
				return null;
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[] { x, y };
			cmd.cmd = 1;
			cmd.args = nargs;
			gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 7: {
			// drawTexture
			if (args.length == 3) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { 0,
						checkInteger(args, 0, "drawTexture"),
						checkInteger(args, 1, "drawTexture"),
						checkInteger(args, 2, "drawTexture") };
				cmd.cmd = 2;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			} else {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { 1,
						checkInteger(args, 0, "drawTexture"),
						checkInteger(args, 1, "drawTexture"),
						checkInteger(args, 2, "drawTexture"),
						checkInteger(args, 3, "drawTexture"),
						checkInteger(args, 4, "drawTexture"),
						checkInteger(args, 5, "drawTexture"),
						checkInteger(args, 6, "drawTexture") };
				cmd.cmd = 2;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			break;
		}
		case 8: {
			// freeTexture
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[] { checkInteger(args, 0, "freeTexture") };
			cmd.cmd = 8;
			cmd.args = nargs;
			gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 9: {
			// line
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[] { checkInteger(args, 0, "line"),
					checkInteger(args, 1, "line"),
					checkInteger(args, 2, "line"),
					checkInteger(args, 3, "line") };
			cmd.cmd = 3;
			cmd.args = nargs;
			gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 10: {
			// getSize
			int tex = gpu.bindedSlot;
			if (args.length >= 1 && isNumber(args[0])) {
				tex = toInteger(args[0]);
			}
			Texture texture = gpu.textures[tex];
			if (texture == null)
				throw new Exception("Texture does not exist");
			return new Object[] { texture.getWidth(), texture.getHeight() };
		}
		case 11: {
			// getPixelColor
			int x = checkInteger(args, 0, "getPixelColor");
			int y = checkInteger(args, 1, "getPixelColor");
			int[] dat = gpu.bindedTexture.getRGB(x, y);
			return new Object[] { dat[0] & 0xFF, dat[1] & 0xFF, dat[2] & 0xFF,
					dat[3] & 0xFF };
		}
		case 12: {
			// rectangle
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[] { checkInteger(args, 0, "rectangle"),
					checkInteger(args, 1, "rectangle"),
					checkInteger(args, 2, "rectangle"),
					checkInteger(args, 3, "rectangle") };
			cmd.cmd = 9;
			cmd.args = nargs;
			gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 13: {
			// filledrectangle
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[] {
					checkInteger(args, 0, "filledRectangle"),
					checkInteger(args, 1, "filledRectangle"),
					checkInteger(args, 2, "filledRectangle"),
					checkInteger(args, 3, "filledRectangle") };
			cmd.cmd = 10;
			cmd.args = nargs;
			gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 14: {
			// getBindedTexture
			return new Object[] { gpu.bindedSlot };
		}
		case 15: {
			// setPixelsRaw
			int x = checkInteger(args, 0, "setPixelsRaw");
			int y = checkInteger(args, 1, "setPixelsRaw");
			int w = checkInteger(args, 2, "setPixelsRaw");
			int h = checkInteger(args, 3, "setPixelsRaw");
			// We send the arguments straight to the GPU!
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[(w * h * 4) + 4 + 1];
			nargs[0] = 0;
			nargs[1] = w;
			nargs[2] = h;
			nargs[3] = x;
			nargs[4] = y;
			Map m = checkTable(args, 4, "setPixelsRaw");
			for (int i = 0; i < (w * h * 4); i++) {
				if (!m.containsKey((double) i))
					throw new Exception("Not enough color values in table");
				nargs[i + 5] = toInteger(m.get((double) i)).shortValue();
			}
			cmd.cmd = 12;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			return ret;
		}
		case 16: {
			// setPixelsRawYX
			int x = checkInteger(args, 0, "setPixelsRawYX");
			int y = checkInteger(args, 1, "setPixelsRawYX");
			int w = checkInteger(args, 2, "setPixelsRawYX");
			int h = checkInteger(args, 3, "setPixelsRawYX");
			// We send the arguments straight to the GPU!
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[(w * h * 4) + 4 + 1];
			nargs[0] = 0;
			nargs[1] = w;
			nargs[2] = h;
			nargs[3] = x;
			nargs[4] = y;
			Map m = checkTable(args, 4, "setPixelsRawYX");
			for (int i = 0; i < (w * h * 4); i++) {
				if (!m.containsKey((double) i))
					throw new Exception("Not enough color values in table");
				nargs[i + 5] = toInteger(m.get((double) i)).shortValue();
			}
			cmd.cmd = 12;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			return ret;
		}
		case 17: {
			// flipTextureV
			// TODO: Remove this function, it isn't needed, we can just use
			// texture binding
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[] { checkInteger(args, 0,
					"flipTextureV") };
			cmd.cmd = 13;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			return ret;
		}
		case 18: {
			// import
			double a = System.currentTimeMillis();
			Byte[] data;
			if (args.length == 1 && isTable(args[0])) {
				// One of the things I hate is that ComputerCraft uses Doubles
				// for all their values
				// Double the fun! -alekso56
				// For fuck's sake, alekso56. -ds84182
				Map m = toTable(args[0]);
				data = new Byte[m.size()];
				for (double i = 0; i < data.length; i++) {
					data[(int) i] = toInteger(m.get(i + 1D)).byteValue();
				}
			} else if (args.length == 1 && isString(args[0])) {
				String file = toLString(args[0]);
				File f = new File(CCLights2.proxy.getWorldDir(worldObj),
						"computer/" + computer.getID() + "/" + file);
				FileInputStream in = new FileInputStream(f);
				byte[] b = new byte[(int) in.getChannel().size()];
				in.read(b);
				in.close();
				data = ArrayUtils.toObject(b);
			} else {
				throw new Exception(
						"bad argument #1 to import (filedata or filename expected, got nil)");
			}
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[] { data };
			cmd.cmd = 14;
			cmd.args = nargs;
			int id = (Integer) gpu.processCommand(cmd)[0];
			Texture tex = gpu.textures[id];
			Object[] ret = { id, tex.getWidth(), tex.getHeight() };
			gpu.drawlist.push(cmd);
			double b = System.currentTimeMillis();
			CCLights2.debug("Import time: " + (b - a) + "ms");
			return ret;
		}
		case 19: {
			// export
			int texid = checkInteger(args, 0, "export");
			String format = checkString(args, 1, "export");
			if (texid < 0 || texid > gpu.textures.length
					|| gpu.textures[texid] == null) {
				throw new Exception("texture does not exist");
			}
			Texture tex = gpu.textures[texid];
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			ImageIO.write(tex.img, format, output);
			byte[] data = output.toByteArray();
			HashMap<Double, Double> out = new HashMap<Double, Double>();
			for (int i = 0; i < data.length; i++) {
				out.put((double) (i + 1), (double) data[i]);
			}
			return new Object[] { out };
		}
		case 20: {
			// drawText
			String str = checkString(args, 0, "drawText");
			int x = checkInteger(args, 1, "drawText");
			int y = checkInteger(args, 2, "drawText");
			Point2D point = gpu.transform.transform(new Point2D.Double(x, y),
					null);
			double tx = point.getX();
			double ty = point.getY();
			int w = gpu.bindedTexture.getWidth();
			int h = gpu.bindedTexture.getHeight();
			double tw = Texture.getStringWidth(str);
			double th = 8;
			if ((tx < 0 && tx + tw < 0) || (ty < 0 && ty + th < 0) || (tx > w)
					|| (ty > h)) // Don't draw if out of bounds!
			{
				return null;
			}
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[2 + str.length()];
			nargs[0] = x;
			nargs[1] = y;
			for (int i = 0; i < str.length(); i++) {
				nargs[2 + i] = str.charAt(i);
			}
			cmd.cmd = 15;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			return ret;
		}
		case 21: {
			// getTextWidth
			String str = checkString(args, 0, "getTextWidth");
			return new Object[] { Texture.getStringWidth(str) };
		}
		case 22: {
			// setColor
			if (args.length > 2) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[4];
				for (int i = 0; i < 4; i++) {
					nargs[i] = args.length > i ? toInteger(args[i]) : 255;
				}
				if (gpu.color.getRed() == (Integer) nargs[0]
						&& gpu.color.getBlue() == (Integer) nargs[1]
						&& gpu.color.getGreen() == (Integer) nargs[2]
						&& gpu.color.getAlpha() == (Integer) nargs[3]) {
					break;
				}
				cmd.cmd = -1;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				break;
			} else {
				throw new Exception("bad argument #" + args.length
						+ " to setColor (number expected got nil)");
			}
		}
		case 23: {
			// getColor
			return new Object[] { gpu.color.getRed(), gpu.color.getGreen(),
					gpu.color.getBlue(), gpu.color.getAlpha() };
		}
		case 24: {
			// translate
			double x = checkNumber(args, 0, "translate");
			double y = checkNumber(args, 1, "translate");
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[2];
			nargs[0] = x;
			nargs[1] = y;
			cmd.cmd = 16;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 25: {
			// rotate
			double r = checkNumber(args, 0, "rotate");
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[1];
			nargs[0] = r;
			cmd.cmd = 17;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 26: {
			// rotateAround
			double r = checkNumber(args, 0, "rotateAround");
			double x = checkNumber(args, 1, "rotateAround");
			double y = checkNumber(args, 2, "rotateAround");
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[3];
			nargs[0] = r;
			nargs[1] = x;
			nargs[2] = y;
			cmd.cmd = 18;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 27: {
			// scale
			double x = checkNumber(args, 0, "scale");
			double y = checkNumber(args, 1, "scale");
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[2];
			nargs[0] = x;
			nargs[1] = y;
			cmd.cmd = 19;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 28: {
			// push
			DrawCMD cmd = new DrawCMD();
			cmd.cmd = 20;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 29: {
			// pop
			DrawCMD cmd = new DrawCMD();
			cmd.cmd = 21;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 30: {
			// getMonitor
			return new Object[] { gpu.currentMonitor.obj };
		}
		case 31: {
			// blur
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[] { checkInteger(args, 0, "blur") };
			cmd.cmd = 22;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			return ret;
		}
		case 32: {
			// startFrame
			frame = true;
			break;
		}
		case 33: {
			// endFrame
			frame = false;
			break;
		}
		case 34: {
			// clearRect
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[] { checkInteger(args, 0, "clearRect"),
					checkInteger(args, 1, "clearRect"),
					checkInteger(args, 2, "clearRect"),
					checkInteger(args, 3, "clearRect") };
			cmd.cmd = 23;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			return ret;
		}
		case 35: {
			// origin
			DrawCMD cmd = new DrawCMD();
			cmd.cmd = 24;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			return ret;
		}
		}
		return null;
	}

	@Override
	public void attach(IComputerAccess computer) {
		comp.add(computer);
		computer.mount("cclights2", new IMount() {
			private static final String RESOURCE_PATH = "/assets/cclights/lua/";
			private final SortedSet<String> files;

			{
				ImmutableSortedSet.Builder<String> files = ImmutableSortedSet
						.naturalOrder();
				InputStream fileList = getClass().getResourceAsStream(
						RESOURCE_PATH + "files.lst");
				if (fileList != null) {
					Scanner sc = new Scanner(fileList);

					while (sc.hasNextLine()) {
						String fileName = sc.nextLine();
						files.add(fileName);
					}

					sc.close();
				}

				this.files = files.build();
			}

			@Override
			public boolean exists(String path) throws IOException {
				return path.isEmpty() || files.contains(path);
			}

			@Override
			public boolean isDirectory(String path) throws IOException {
				return path.isEmpty();
			}

			@Override
			public void list(String path, List<String> contents)
					throws IOException {
				contents.addAll(files);
			}

			@Override
			public long getSize(String path) throws IOException {
				return 0;
			}

			@Override
			public InputStream openForRead(String path) throws IOException {
				if (!files.contains(path))
					throw new IOException();
				return getClass().getResourceAsStream(RESOURCE_PATH + path);
			}

		});
	}

	@Override
	public void detach(IComputerAccess computer) {
		comp.remove(computer);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setIntArray("addedTypes", addedType);
		nbt.setInteger("vram", gpu.maxmem);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		addedType = nbt.getIntArray("addedTypes");
		if (addedType == null) {
			addedType = new int[1025];
		} else if (addedType.length != 1025) {
			addedType = new int[1025];
		}
		int init = gpu.maxmem;
		gpu.maxmem = nbt.getInteger("vram");
		if (init > gpu.maxmem) {
			gpu.maxmem = init;
		}
	}

	public void connectToMonitor() {
		for (int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++) {
			ForgeDirection dir = ForgeDirection.VALID_DIRECTIONS[i];
			TileEntity ftile = worldObj.getBlockTileEntity(
					xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord
							+ dir.offsetZ);
			if (ftile != null) {
				if (ftile instanceof TileEntityMonitor) {
					TileEntityMonitor tile = (TileEntityMonitor) worldObj
							.getBlockTileEntity(xCoord + dir.offsetX, yCoord
									+ dir.offsetY, zCoord + dir.offsetZ);
					if (tile != null) {
						boolean found = false;
						for (Monitor m : gpu.monitors) {
							if (m == tile.mon) {
								found = true;
								break;
							}
						}
						if (found)
							break;
						tile.connect(this.gpu);
						tile.mon.tex.fill(Color.black);
						tile.mon.tex.drawText("Monitor connected", 0, 0,
								Color.white);
						tile.mon.tex.texUpdate();
						gpu.setMonitor(tile.mon);
						return;
					}
				}
			}
		}
	}

	@Override
	public synchronized void updateEntity() {
		synchronized (this) {
			if (!frame) {
				gpu.processSendList();
			}
		}
		connectToMonitor();
		if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT
				&& ticks++ % 20 == 0 && !sentOnce) {
			PacketSenders.GPUDOWNLOAD(xCoord, yCoord, zCoord);
			sentOnce = true;
		}

	}

	@Override
	public boolean equals(IPeripheral other) {
		if (other.getType() == getType()) {
			return true;
		} else
			return false;
	}
}
