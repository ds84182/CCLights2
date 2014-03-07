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
import dan200.computer.api.IComputerAccess;
import dan200.computer.api.ILuaContext;
import dan200.computer.api.IMount;
import dan200.computer.api.IPeripheral;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.converter.ConvertDouble;
import ds.mods.CCLights2.converter.ConvertInteger;
import ds.mods.CCLights2.converter.ConvertString;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.network.PacketSenders;

public class TileEntityGPU extends TileEntity implements IPeripheral {
	public GPU gpu;
	public int ticks;
	public ArrayList<DrawCMD> newarr = new ArrayList<DrawCMD>();
	public ArrayList<IComputerAccess> comp = new ArrayList<IComputerAccess>();
	public int emptyFor = 0;
	public TreeMap<String, Integer> playerToClickMap = new TreeMap<String, Integer>();
	public TreeMap<Integer, int[]> clickToDataMap = new TreeMap<Integer, int[]>();
	public Random rand = new Random();
	public int[] addedType = new int[1025];
	public boolean frame = false;
	private boolean sentOnce = false;

	public TileEntityGPU() {
		gpu = new GPU(1024 * 8);
		gpu.tile = this;
	}

	public void startClick(Player player, int button, int x, int y) {
		int id = rand.nextInt();
		while (playerToClickMap.containsValue(id)) {
			id = rand.nextInt();
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
						CCLights2.debug("Connecting!");
						tile.connect(this.gpu);
						tile.mon.tex.fill(Color.black);
						tile.mon.tex.drawText("Monitor connected", 0, 0, Color.white);
						tile.mon.tex.texUpdate();
						gpu.setMonitor(tile.mon);
						return;
					}
				}
			}
		}
	}

	@Override
	public String getType() {
		return "GPU";
	}

	@Override
	public String[] getMethodNames() {
		return new String[] {
				"fill", "createTexture", "getFreeMemory",
				"getTotalMemory", "getUsedMemory", "bindTexture", "plot",
				"drawTexture", "freeTexture", "line", "getSize",
				"getPixels", "rectangle", "filledRectangle",
				"getBindedTexture", "setPixelsRaw", "setPixelsRawYX",
				"flipTextureV", "import", "export", "drawText", "getTextWidth",
				"setColor", "getColor", "translate", "rotate", "rotateAround",
				"scale", "push", "pop", "getMonitor", "blur", "startFrame",
				"endFrame", "clearRect" };
	}

	@SuppressWarnings("rawtypes")
	@Override
	public synchronized Object[] callMethod(IComputerAccess computer,
			ILuaContext context, int method, Object[] args) throws Exception {
		switch (method) {
		case 0: {
			//fill
			DrawCMD cmd = new DrawCMD();
			cmd.cmd = 0;
			gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 1: {
			//createTexture
			if (args.length > 1) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { ConvertInteger.convert(args[0]),
						ConvertInteger.convert(args[1]) };
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
			else
			{
				throw new Exception("createTexture: Argument Error: width, height expected");
			}
		}
		case 2: {
			//getFreeMemory
			return new Object[] { gpu.getFreeMemory() };
		}
		case 3: {
			//getTotalMemory
			return new Object[] { gpu.maxmem };
		}
		case 4: {
			//getUsedMemory
			return new Object[] { gpu.getUsedMemory() };
		}
		case 5: {
			//bindTexture
			if (args.length > 0) {
				if (gpu.textures[ConvertInteger.convert(args[0])] == null)
					throw new Exception("bindTexture: Texture does not exist");
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { ConvertInteger.convert(args[0]) };
				cmd.cmd = 7;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("bindTexture: Argument Error: textureid expected");
			}
			break;
		}
		case 6: {
			//was plot and setColorRGB is now plot
			if (args.length >= 2) {
				int x = ConvertInteger.convert(args[0]);
				int y = ConvertInteger.convert(args[1]);
				Point2D point = gpu.transform.transform(new Point2D.Double(x, y),null);
				double tx = point.getX();
				double ty = point.getY();
				int w = gpu.bindedTexture.getWidth();
				int h = gpu.bindedTexture.getHeight();
				if (tx<0 || ty<0 || tx>w || ty>h) //Don't draw if out of bounds!
					return null;
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { x, y };
				cmd.cmd = 1;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("plot: Argument Error: x, y expected");
			}
			break;
		}
		case 7: {
			//drawTexture
			if (args.length == 3) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { 0, ConvertInteger.convert(args[0]),
						ConvertInteger.convert(args[1]), ConvertInteger.convert(args[2]) };
				cmd.cmd = 2;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			} else if (args.length > 6) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { 1, ConvertInteger.convert(args[0]),
						ConvertInteger.convert(args[1]), ConvertInteger.convert(args[2]),
						ConvertInteger.convert(args[3]), ConvertInteger.convert(args[4]),
						ConvertInteger.convert(args[5]), ConvertInteger.convert(args[6]) };
				cmd.cmd = 2;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("drawTexture: Argument Error: textureid, x, y expected");
			}
			break;
		}
		case 8: {
			//freeTexture
			if (args.length == 1) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { ConvertInteger.convert(args[0]) };
				cmd.cmd = 8;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("freeTexture: Argument Error: textureid expected");
			}
			break;
		}
		case 9: {
			//line
			if (args.length > 3) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { ConvertInteger.convert(args[0]),
						ConvertInteger.convert(args[1]), ConvertInteger.convert(args[2]),
						ConvertInteger.convert(args[3]) };
				cmd.cmd = 3;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("line: Argument Error: x1, y1, x2, y2 expected");
			}
			break;
		}
		case 10: {
			//getSize
			int tex = gpu.bindedSlot;
			if (args.length >= 1)
			{
				tex = ConvertInteger.convert(args[0]);
			}
			if (gpu.textures[tex] == null)
				throw new Exception("getMonitorSize: texture does not exist");
			Texture texture = gpu.textures[tex];
			return new Object[] { texture.getWidth(),texture.getHeight() };
		}
		case 11: {
			//getPixelColor
			if (args.length > 1) {
				int x = ConvertInteger.convert(args[0]);
				int y = ConvertInteger.convert(args[1]);
				int[] dat = gpu.bindedTexture.getRGB(x, y);
				return new Object[] { dat[0] & 0xFF, dat[1] & 0xFF, dat[2] & 0xFF, dat[3] & 0xFF };
			}
			else
			{
				throw new Exception("getPixelColor: Argument Error: x, y expected");
			}
		}
		case 12: {
			//rectangle
			if (args.length > 3) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { ConvertInteger.convert(args[0]),
						ConvertInteger.convert(args[1]), ConvertInteger.convert(args[2]),
						ConvertInteger.convert(args[3]) };
				cmd.cmd = 9;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("rectangle: Argument Error: x, y, width, height expected");
			}
			break;
		}
		case 13: {
			//filledrectangle
			if (args.length > 3) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { ConvertInteger.convert(args[0]),
						ConvertInteger.convert(args[1]), ConvertInteger.convert(args[2]),
						ConvertInteger.convert(args[3]) };
				cmd.cmd = 10;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("filledRectangle: Argument Error: x, y, width, height expected");
			}
			break;
		}
		case 14: {
			//getBindedTexture
			return new Object[] { gpu.bindedSlot };
		}
		case 15: {
			//setPixelsRaw
			if (args.length < 4) {
				throw new Exception("setPixelsRaw: Argument Error: w, h, x, y, {[r,g,b,a]}... expected");
			} else {
				int w = ConvertInteger.convert(args[0]);
				int h = ConvertInteger.convert(args[1]);
				// We send the arguments straight to the GPU!
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[(w * h * 4) + 4 + 1];
				nargs[0] = 0;
				nargs[1] = w;
				nargs[2] = h;
				nargs[3] = ConvertInteger.convert(args[2]);
				nargs[4] = ConvertInteger.convert(args[3]);
				Map m = (Map) args[4];
				for (int i = 0; i < (w * h * 4); i++) {
					nargs[i + 4] = ConvertInteger.convert(m.get((double) i)).shortValue();
				}
				cmd.cmd = 12;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				return ret;
			}
		}
		case 16: {
			//setPixelsRawYX
			if (args.length < 4) {
				throw new Exception("setPixelsRawYX: Argument Error: w, h, x, y, [r,g,b]... expected");
			} else {
				int w = ConvertInteger.convert(args[0]);
				int h = ConvertInteger.convert(args[1]);
				// We send the arguments straight to the GPU!
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[(w * h * 3) + 4 + 1];
				nargs[0] = 1;
				nargs[1] = w;
				nargs[2] = h;
				nargs[3] = ConvertInteger.convert(args[2]);
				nargs[4] = ConvertInteger.convert(args[3]);
				Map m = (Map) args[4];
				for (int i = 0; i < (w * h * 3); i++) {
					nargs[i + 4] = ConvertInteger.convert(m.get((double) i)).shortValue();
				}
				cmd.cmd = 12;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				return ret;
			}
		}
		case 17: {
			//flipTextureV
			if (args.length > 0) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { ConvertInteger.convert(args[0]) };
				cmd.cmd = 13;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				return ret;
			}
			else{
				throw new Exception("Number expected.");
			}
		}
		case 18: {
			//import
			double a = System.currentTimeMillis();
			Byte[] data;
			if (args.length == 1 && args[0] instanceof Map)
			{
				//One of the things I hate is that ComputerCraft uses Doubles for all their values
				//Double the fun! -alekso56
				Map m = (Map)args[0];
				data = new Byte[m.size()];
				for (double i = 0; i<data.length; i++)
				{
					data[(int) i] = ((Double)m.get(i+1D)).byteValue();
				}
				CCLights2.debug("Converted data");
			}
			else if (args.length == 1 && args[0] instanceof String)
			{
				String file = (String)args[0];
				File f = new File(CCLights2.proxy.getWorldDir(worldObj),"computer/"+computer.getID()+"/"+file);
				FileInputStream in = new FileInputStream(f);
				byte[] b = new byte[(int)in.getChannel().size()];
				in.read(b);
				in.close();
				data = ArrayUtils.toObject(b);
			}
			else
			{
				throw new Exception("import: Argument Error: (filedata or filename)");
			}
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[]{data};
			cmd.cmd = 14;
			cmd.args = nargs;
			int id = (Integer) gpu.processCommand(cmd)[0];
			Texture tex = gpu.textures[id];
			Object[] ret = {id,tex.getWidth(),tex.getHeight()};
			gpu.drawlist.push(cmd);
			double b = System.currentTimeMillis();
			CCLights2.debug("Import time: "+(b-a)+"ms");
			return ret;
		}
		case 19:
		{
			//export
			if (args.length > 1)
			{
				int texid = ConvertInteger.convert(args[0]);
				String format = ConvertString.convert(args[1]);
				if (texid<0 || texid>gpu.textures.length || gpu.textures[texid] == null)
				{
					throw new Exception("Texture does not exist.");
				}
				Texture tex = gpu.textures[texid];
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				ImageIO.write(tex.img, format, output);
				byte[] data = output.toByteArray();
				HashMap<Double,Double> out = new HashMap<Double, Double>();
				for (int i = 0; i<data.length; i++)
				{
					out.put((double)(i+1), (double)data[i]);
				}
				return new Object[]{out};
			}
			else
			{
				throw new Exception("export: Argument Error: textureid, format expected");
			}
		}
		case 20:
		{
			//Drawtext
			if (args.length > 2)
			{
				String str = ConvertString.convert(args[0]);
				int x = ConvertInteger.convert(args[1]);
				int y = ConvertInteger.convert(args[2]);
				Point2D point = gpu.transform.transform(new Point2D.Double(x, y),null);
				double tx = point.getX();
				double ty = point.getY();
				int w = gpu.bindedTexture.getWidth();
				int h = gpu.bindedTexture.getHeight();
				double tw = Texture.getStringWidth(str);
				double th = 8;
				if ((tx<0 && tx+tw<0) || (ty<0 && ty+th<0) || (tx>w) || (ty>h)) //Don't draw if out of bounds!
				{
					return null;
				}
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[2+str.length()];
				nargs[0] = x;
				nargs[1] = y;
				for (int i=0; i<str.length(); i++)
				{
					nargs[2+i] = str.charAt(i);
				}
				cmd.cmd = 15;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				return ret;
			}
			else
			{
				throw new Exception("drawText: Argument Error: text, x, y expected");
			}
		}
		case 21:
		{
			//getTextWidth
			if (args.length > 0)
			{
				String str = ConvertString.convert(args[0]);
				return new Object[]{Texture.getStringWidth(str)};
			}
			else
			{
				throw new Exception("getTextWidth: Argument Error: text expected");
			}
		}
		case 22:
		{
			//setColor
			if (args.length > 2)
			{
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[4];
				for (int i=0; i<4; i++)
				{
					nargs[i] = args.length > i ? ConvertInteger.convert(args[i]) : 255;
				}
				if (gpu.color.getRed() == (Integer)nargs[0] && gpu.color.getBlue() == (Integer)nargs[1] && gpu.color.getGreen() == (Integer)nargs[2] && gpu.color.getAlpha() == (Integer)nargs[3])
				{
					break;
				}
				cmd.cmd = -1;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				break;
			}
			else
			{
				throw new Exception("setColor: Argument Error: int, int, int[, int] expected");
			}
		}
		case 23:
		{
			//getColor
			return new Object[]{gpu.color.getRed(),gpu.color.getGreen(),gpu.color.getBlue(),gpu.color.getAlpha()};
		}
		case 24:
		{
			//translate
			double x = ConvertDouble.convert(args[0]);
			double y = ConvertDouble.convert(args[1]);
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
		case 25:
		{
			//rotate
			double r = ConvertDouble.convert(args[0]);
			DrawCMD cmd = new DrawCMD();
			Object[] nargs = new Object[1];
			nargs[0] = r;
			cmd.cmd = 17;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 26:
		{
			//rotateAround
			double r = ConvertDouble.convert(args[0]);
			double x = ConvertDouble.convert(args[1]);
			double y = ConvertDouble.convert(args[2]);
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
		case 27:
		{
			//scale
			double x = ConvertDouble.convert(args[0]);
			double y = ConvertDouble.convert(args[1]);
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
		case 28:
		{
			//push
			DrawCMD cmd = new DrawCMD();
			cmd.cmd = 20;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 29:
		{
			//pop
			DrawCMD cmd = new DrawCMD();
			cmd.cmd = 21;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 30:
		{
			//getMonitor
			return new Object[]{gpu.currentMonitor.obj};
		}
		case 31:
		{
			//blur
			if (args.length > 0) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { ConvertInteger.convert(args[0]) };
				cmd.cmd = 22;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				return ret;
			}
			else
			{
				throw new Exception("blur: Argument Error: textureid expected");
			}
		}
		case 32:
		{
			//startFrame
			frame = true;
			break;
		}
		case 33:
		{
			//endFrame
			frame = false;
			break;
		}
		case 34:
		{
			//clearRect
			if (args.length >= 4) {
				DrawCMD cmd = new DrawCMD();
				Object[] nargs = new Object[] { ConvertInteger.convert(args[0]),ConvertInteger.convert(args[1]),ConvertInteger.convert(args[2]),ConvertInteger.convert(args[3]) };
				cmd.cmd = 23;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				return ret;
			}
			else
			{
				throw new Exception("clearRect: Argument Error: x, y, width, height expected");
			}
		}
		}
		return null;
	}

	@Override
	public boolean canAttachToSide(int side) {
		return true;
	}

	@Override
	public void attach(IComputerAccess computer) {
		comp.add(computer);
		computer.mount("cclights2", new IMount(){
			private static final String RESOURCE_PATH = "/assets/cclights/lua/";
			private final SortedSet<String> files;

			{
				ImmutableSortedSet.Builder<String> files = ImmutableSortedSet.naturalOrder();
				InputStream fileList = getClass().getResourceAsStream(RESOURCE_PATH + "files.lst");
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
			public void list(String path, List<String> contents) throws IOException {
				contents.addAll(files);
			}

			@Override
			public long getSize(String path) throws IOException {
				return 0;
			}

			@Override
			public InputStream openForRead(String path) throws IOException {
				if (!files.contains(path)) throw new IOException();
				return getClass().getResourceAsStream(RESOURCE_PATH + path);
			}

		});
	}

	@Override
	public void detach(IComputerAccess computer) {
		comp.remove(computer);
		computer.unmount("cclights2");
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

	@Override
	public synchronized void updateEntity() {
		synchronized (this) {if (!frame){ gpu.processSendList();}}
		ticks++;
		if (ticks % 20 == 0)
			connectToMonitor();
		if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT && ticks % 20 == 0 && !sentOnce) {
			PacketSenders.GPUDOWNLOAD(xCoord, yCoord, zCoord, worldObj.provider.dimensionId);
			this.sentOnce=true;
		}

	}
}