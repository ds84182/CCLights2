package ds.mods.CCLights2.block.tileentity;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.ForgeDirection;
import ds.mods.CCLights2.GPU;
import ds.mods.CCLights2.Monitor;

public class MonitorBase extends TileEntity {
	public Monitor mon;
	
	public MonitorBase()
	{
		mon = new Monitor(256,144);
		mon.tex.fill(0, 0, 0);
	}
	
	public void connect(GPU g)
	{
		mon.addGPU(g);
	}
}
