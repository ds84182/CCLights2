package ds.mods.CCLights2.block.tileentity;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.ForgeDirection;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import dan200.computer.api.IComputerAccess;
import dan200.computer.api.ILuaContext;
import dan200.computer.api.IPeripheral;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.Config;
import ds.mods.CCLights2.converter.ConvertDouble;
import ds.mods.CCLights2.converter.ConvertInteger;
import ds.mods.CCLights2.converter.ConvertString;
import ds.mods.CCLights2.debug.DebugWindow;
import ds.mods.CCLights2.gpu.DrawCMD;
import ds.mods.CCLights2.gpu.GPU;
import ds.mods.CCLights2.gpu.Monitor;
import ds.mods.CCLights2.gpu.Texture;
import ds.mods.CCLights2.gpu.imageLoader.ImageLoader;
import ds.mods.CCLights2.network.PacketHandler;

public class TileEntityGPU extends TileEntity implements IPeripheral {
	public GPU gpu;
	public int ticks;
	public boolean sendDLREQ = false;
	public ArrayList<DrawCMD> newarr = new ArrayList<DrawCMD>();
	public ArrayList<IComputerAccess> comp = new ArrayList<IComputerAccess>();
	public int emptyFor = 0;
	public TreeMap<String, Integer> playerToClickMap = new TreeMap<String, Integer>();
	public TreeMap<Integer, int[]> clickToDataMap = new TreeMap<Integer, int[]>();
	public Random rand = new Random();
	public int[] addedType = new int[1025];
	public boolean frame = false;
	public DebugWindow wind;

	public TileEntityGPU() {
		gpu = new GPU(1024 * 8);
		gpu.tile = this;
		if (CCLights2.proxy.getClientWorld() != null) {
			// download textures//
			System.out.println("Client side GPU!");
			sendDLREQ = true;
		} else {
			System.out.println("Server side GPU!");
		}
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
						System.out.println("Connecting!");
						tile.connect(this.gpu);
						tile.mon.tex.fill(Color.black);
						tile.mon.tex.drawText("Monitor connected", 0, 0, Color.white);
						gpu.setMonitor(tile.mon);
						return;
					}
				} else {
					if (Config.DEBUGS) {
						System.out.println(dir.name());
						System.out.println(ftile.toString());
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
				"getTotalMemory", "getUsedMemory", "bindTexture",
				"setColorRGB", "plot", "drawTexture", "freeTexture", "line",
				"getSize", "getTextureSize", "setTransparent",
				"setTransparencyColor", "getColorRGB", "getPixel", "rectangle",
				"filledRectangle", "setBPP", "getBindedTexture", "getBPP",
				"getNativePixel", "setPixels", "setPixelsYX", "flipTextureV",
				"import", "export", "drawText", "getTextWidth", "setColor", "getColor",
				"translate", "rotate", "rotateAround", "scale", "push", "pop",
				"getMonitor", "blur", "startFrame", "endFrame", "clearRect" };
	}

	@Override
	public synchronized Object[] callMethod(IComputerAccess computer,
			ILuaContext context, int method, Object[] args) throws Exception {
		//System.out.println(getMethodNames()[method]);
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
				double[] nargs = new double[] { ConvertInteger.convert(args[0]),
						ConvertInteger.convert(args[1]) };
				cmd.cmd = 6;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				int id = (Integer) ret[0];
				if (id == -1) {
					throw new Exception("createTexture: not enough memory");
				} else if (id == -2) {
					throw new Exception("createTexture: not enough texture slots");
				} else {
					gpu.drawlist.push(cmd);
					return ret;
				}
			}
			else
			{
				throw new Exception("createTexture: argument error: number, number expected");
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
					throw new Exception("bindTexture: texture does not exist");
				DrawCMD cmd = new DrawCMD();
				double[] nargs = new double[] { ConvertInteger.convert(args[0]) };
				cmd.cmd = 7;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("bindTexture: argument error: number expected");
			}
			break;
		}
		case 6:
		case 7: {
			//plot and setColorRGB
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
				double[] nargs = new double[] { x, y };
				cmd.cmd = 1;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("plot/setColorRGB: argument error: number, number expected");
			}
			break;
		}
		case 8: {
			//drawTexture
			if (args.length == 3) {
				DrawCMD cmd = new DrawCMD();
				double[] nargs = new double[] { 0, ConvertInteger.convert(args[0]),
						ConvertInteger.convert(args[1]), ConvertInteger.convert(args[2]) };
				cmd.cmd = 2;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			} else if (args.length > 6) {
				DrawCMD cmd = new DrawCMD();
				double[] nargs = new double[] { 1, ConvertInteger.convert(args[0]),
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
				throw new Exception("drawTexture: argument error: number, number, number or numberx7 expected");
			}
			break;
		}
		case 9: {
			//freeTexture
			if (args.length == 1) {
				DrawCMD cmd = new DrawCMD();
				double[] nargs = new double[] { ConvertInteger.convert(args[0]) };
				cmd.cmd = 8;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("freeTexture: argument error: number expected");
			}
			break;
		}
		case 10: {
			//line
			if (args.length > 3) {
				DrawCMD cmd = new DrawCMD();
				double[] nargs = new double[] { ConvertInteger.convert(args[0]),
						ConvertInteger.convert(args[1]), ConvertInteger.convert(args[2]),
						ConvertInteger.convert(args[3]) };
				cmd.cmd = 3;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("line: argument error: numberx4 expected");
			}
			break;
		}
		case 11:
		case 12: {
			//getSize and getTextureSize
			int tex = gpu.bindedSlot;
			if (args.length >= 1)
			{
				tex = ConvertInteger.convert(args[0]);
			}
			if (gpu.textures[tex] == null)
				throw new Exception("getTextureSize: texture does not exist");
			Texture texture = gpu.textures[tex];
			return new Object[] { texture.getWidth(),
					texture.getHeight() };
		}
		case 13: {
			throw new Exception("Transparency is depreciated.");
		}
		case 14: {
			throw new Exception("Transparency is depreciated.");
		}
		case 15:
		case 16: {
			//getPixel and getColorRGB
			if (args.length > 1) {
				int x = ConvertInteger.convert(args[0]);
				int y = ConvertInteger.convert(args[1]);
				int[] dat = gpu.bindedTexture.getRGB(x, y);
				return new Object[] { dat[0] & 0xFF, dat[1] & 0xFF, dat[2] & 0xFF };
			}
			else
			{
				throw new Exception("getPixel/getColorRGB: argument error: number, number expected");
			}
		}
		case 17: {
			//rectangle
			if (args.length > 3) {
				DrawCMD cmd = new DrawCMD();
				double[] nargs = new double[] { ConvertInteger.convert(args[0]),
						ConvertInteger.convert(args[1]), ConvertInteger.convert(args[2]),
						ConvertInteger.convert(args[3]) };
				cmd.cmd = 9;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("rectangle: argument error: x, y, width, height expected");
			}
			break;
		}
		case 18: {
			//filledRectangle
			if (args.length > 3) {
				DrawCMD cmd = new DrawCMD();
				double[] nargs = new double[] { ConvertInteger.convert(args[0]),
						ConvertInteger.convert(args[1]), ConvertInteger.convert(args[2]),
						ConvertInteger.convert(args[3]) };
				cmd.cmd = 10;
				cmd.args = nargs;
				gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
			}
			else
			{
				throw new Exception("filledRectangle: argument error: x, y, width, height expected");
			}
			break;
		}
		case 19: {
			throw new Exception("Bit Depths are depricated.");
		}
		case 20: {
			return new Object[] { gpu.bindedSlot };
		}
		case 21: {
			throw new Exception("Bit Depths are depeciated.");
		}
		case 22: {
			throw new Exception("getPixelNative is disfunctional.");
		}
		case 23: {
			if (args.length < 4) {
				throw new Exception("w, h, x, y, {[r,g,b]}... expected");
			} else {
				int w = ConvertInteger.convert(args[0]);
				int h = ConvertInteger.convert(args[1]);
				// We send the arguments straight to the GPU!
				DrawCMD cmd = new DrawCMD();
				double[] nargs = new double[(w * h * 3) + 4 + 1];
				nargs[0] = 0;
				nargs[1] = w;
				nargs[2] = h;
				nargs[3] = ConvertInteger.convert(args[2]);
				nargs[4] = ConvertInteger.convert(args[3]);
				Map m = (Map) args[4];
				for (int i = 0; i < (w * h * 3); i++) {
					nargs[i + 4] = ConvertInteger.convert(m.get((double) i));
				}
				cmd.cmd = 12;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				return ret;
			}
		}
		case 24: {
			if (args.length < 4) {
				throw new Exception("w, h, x, y, [r,g,b]... expected");
			} else {
				int w = ConvertInteger.convert(args[0]);
				int h = ConvertInteger.convert(args[1]);
				// We send the arguments straight to the GPU!
				DrawCMD cmd = new DrawCMD();
				double[] nargs = new double[(w * h * 3) + 4 + 1];
				nargs[0] = 1;
				nargs[1] = w;
				nargs[2] = h;
				nargs[3] = ConvertInteger.convert(args[2]);
				nargs[4] = ConvertInteger.convert(args[3]);
				Map m = (Map) args[4];
				for (int i = 0; i < (w * h * 3); i++) {
					nargs[i + 4] = ConvertInteger.convert(m.get((double) i));
				}
				cmd.cmd = 12;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				return ret;
			}
		}
		case 25: {
			if (args.length > 0) {
				DrawCMD cmd = new DrawCMD();
				double[] nargs = new double[] { ConvertInteger.convert(args[0]) };
				cmd.cmd = 13;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				return ret;
			}
		}
		case 26: {
			if (args.length > 1) {
				int size = 0;
				//One of the things I hate is that ComputerCraft uses Doubles for all their values
				Map m = (Map)args[0];
				String format = (String)args[1];
				for (double i = 1; i<Double.MAX_VALUE; i++)
				{
					if (m.containsKey(i))
						size = (int) i;
					else
						break;
				}
				//System.out.println("SIze "+size);
				byte[] data = new byte[size];
				for (double i = 0; i<data.length; i++)
				{
					//System.out.println("AT"+i);
					//System.out.println(((Double)m.get(i+1D)).byteValue());
					data[(int) i] = ((Double)m.get(i+1D)).byteValue();
				}
				System.out.println("Moved data");
				BufferedImage img = ImageLoader.load(data, format);
				System.out.println("Imaged loaded "+img);
				int w = img.getWidth();
				int h =  img.getHeight();
				DrawCMD cmd = new DrawCMD();
				double[] nargs = new double[(w * h) + 2];
				nargs[0] = w;
				nargs[1] = h;
				int i = 2;
				for (int x = 0; x<img.getWidth(); x++)
				{
					//System.out.println(x);
					for (int y = 0; y<img.getHeight(); y++)
					{
						nargs[i++] = img.getRGB(x, y);
					}
				}
				cmd.cmd = 14;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				return ret;
			}
		}
		case 27:
		{
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
		}
		case 28:
		{
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
				double[] nargs = new double[2+str.length()];
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
		}
		case 29:
		{
			if (args.length > 0)
			{
				String str = ConvertString.convert(args[0]);
				return new Object[]{Texture.getStringWidth(str)};
			}
		}
		case 30:
		{
			if (args.length > 2)
			{
				DrawCMD cmd = new DrawCMD();
				double[] nargs = new double[4];
				for (int i=0; i<4; i++)
				{
					nargs[i] = args.length > i ? ConvertInteger.convert(args[i]) : 255;
				}
				if (gpu.color.getRed() == nargs[0] && gpu.color.getBlue() == nargs[1] && gpu.color.getGreen() == nargs[2] && gpu.color.getAlpha() == nargs[3])
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
				throw new Exception("int, int, int[, int] expected");
			}
		}
		case 31:
		{
			return new Object[]{gpu.color.getRed(),gpu.color.getGreen(),gpu.color.getBlue(),gpu.color.getAlpha()};
		}
		case 32:
		{
			double x = ConvertDouble.convert(args[0]);
			double y = ConvertDouble.convert(args[1]);
			DrawCMD cmd = new DrawCMD();
			double[] nargs = new double[2];
			nargs[0] = x;
			nargs[1] = y;
			cmd.cmd = 16;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 33:
		{
			double r = ConvertDouble.convert(args[0]);
			DrawCMD cmd = new DrawCMD();
			double[] nargs = new double[1];
			nargs[0] = r;
			cmd.cmd = 17;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 34:
		{
			double r = ConvertDouble.convert(args[0]);
			double x = ConvertDouble.convert(args[1]);
			double y = ConvertDouble.convert(args[2]);
			DrawCMD cmd = new DrawCMD();
			double[] nargs = new double[3];
			nargs[0] = r;
			nargs[1] = x;
			nargs[2] = y;
			cmd.cmd = 18;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 35:
		{
			double x = ConvertDouble.convert(args[0]);
			double y = ConvertDouble.convert(args[1]);
			DrawCMD cmd = new DrawCMD();
			double[] nargs = new double[2];
			nargs[0] = x;
			nargs[1] = y;
			cmd.cmd = 19;
			cmd.args = nargs;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 36:
		{
			DrawCMD cmd = new DrawCMD();
			cmd.cmd = 20;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 37:
		{
			DrawCMD cmd = new DrawCMD();
			cmd.cmd = 21;
			Object[] ret = gpu.processCommand(cmd);
			gpu.drawlist.push(cmd);
			break;
		}
		case 38:
		{
			return new Object[]{gpu.currentMonitor.obj};
		}
		case 39:
		{
			//blur
			if (args.length > 0) {
				DrawCMD cmd = new DrawCMD();
				double[] nargs = new double[] { ConvertInteger.convert(args[0]) };
				cmd.cmd = 22;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				return ret;
			}
			else
			{
				throw new Exception("blur: argument error: number expected");
			}
		}
		case 40:
		{
			//startFrame
			frame = true;
			break;
		}
		case 41:
		{
			//endFrame
			frame = false;
			break;
		}
		case 42:
		{
			if (args.length >= 4) {
				DrawCMD cmd = new DrawCMD();
				double[] nargs = new double[] { ConvertInteger.convert(args[0]),ConvertInteger.convert(args[1]),ConvertInteger.convert(args[2]),ConvertInteger.convert(args[3]) };
				cmd.cmd = 23;
				cmd.args = nargs;
				Object[] ret = gpu.processCommand(cmd);
				gpu.drawlist.push(cmd);
				return ret;
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

	@Override
	public synchronized void updateEntity() {
		synchronized (this) {if (!frame) gpu.processSendList();}
		connectToMonitor();
		if (sendDLREQ & ticks++ % 20 == 0) {
			Packet250CustomPayload packet = new Packet250CustomPayload();
			packet.channel = "CCLights2";
			ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
			DataOutputStream outputStream = new DataOutputStream(bos);
			try {
				outputStream.writeByte(PacketHandler.NET_GPUDOWNLOAD);
				outputStream.writeInt(xCoord);
				outputStream.writeInt(yCoord);
				outputStream.writeInt(zCoord);
				outputStream.writeInt(worldObj.provider.dimensionId);
			} catch (IOException e) {
				e.printStackTrace();
			}
			packet.data = bos.toByteArray();
			packet.length = bos.size();
			System.out.println("Sent DL Request to server!");
			PacketDispatcher.sendPacketToServer(packet);
			sendDLREQ = false;
		}
	}
}