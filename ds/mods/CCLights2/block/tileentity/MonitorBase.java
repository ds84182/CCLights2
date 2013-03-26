package ds.mods.CCLights2.block.tileentity;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.ForgeDirection;
import ds.mods.CCLights2.GPU;
import ds.mods.CCLights2.Monitor;

public class MonitorBase extends TileEntity {
	public Monitor mon;
	public GPU gpu;
	public TileEntityGPU gputile;
	public ForgeDirection gpudir;
	
	public MonitorBase()
	{
		mon = new Monitor(256,144);
		mon.tex.fill(0, 0, 0);
	}
	
	public boolean isGPUConnected()
	{
		if (gpudir == null)
			return false;
		TileEntityGPU tile = (TileEntityGPU) worldObj.getBlockTileEntity(xCoord+gpudir.offsetX, yCoord+gpudir.offsetY, zCoord+gpudir.offsetZ);
		if (gputile == null)
			return false;
		return gputile.mon == mon & gputile.gpu == gpu & gputile == tile;
	}
	
	public void connect(GPU g, ForgeDirection dir, TileEntityGPU tile)
	{
		gpu = g;
		gpudir = dir.getOpposite();
		gputile = tile;
	}
}
