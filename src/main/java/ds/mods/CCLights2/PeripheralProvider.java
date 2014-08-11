package ds.mods.CCLights2;

import net.minecraft.world.World;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;

public class PeripheralProvider implements IPeripheralProvider
{

    @Override
    public IPeripheral getPeripheral(World world, int x, int y, int z, int side)
    {
        if(world.getBlockTileEntity(x, y, z) instanceof IPeripheral)
        {
            return (IPeripheral) world.getBlockTileEntity(x, y, z);
        }

        return null;
    }
}