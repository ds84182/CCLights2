package ds.mods.CCLights2.serialize;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityGPU;
import ds.mods.CCLights2.gpu.GPU;

public class GPUSerializer implements ISerializer {

	@Override
	public void write(Object o, ByteArrayDataOutput dat) {
		GPU g = (GPU)o;
		dat.writeInt(g.tile.xCoord);
		dat.writeInt(g.tile.yCoord);
		dat.writeInt(g.tile.zCoord);
		dat.writeInt(g.tile.worldObj.provider.dimensionId);
	}

	@Override
	public Object read(ByteArrayDataInput dat) {
		
		int x = dat.readInt();
		int y = dat.readInt();
		int z = dat.readInt();
		int d = dat.readInt();
		
		//System.out.printf("GPU in dim %d at %d, %d, %d\n", d, x, y, z);
		
		World world = CCLights2.proxy.getClientWorld();
		
		TileEntity noncast = world.getBlockTileEntity(x, y, z);
		if (noncast != null)
		{
			TileEntityGPU g = (TileEntityGPU) noncast;
			return g.gpu;
		}
		
		return null;
	}

}
