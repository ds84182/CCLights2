package ds.mods.CCLights2.block.tileentity;

import java.util.Arrays;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import dan200.computer.api.IComputerAccess;
import dan200.computer.api.ILuaContext;
import dan200.computer.api.IPeripheral;

public class TileEntityColorLight  extends TileEntity implements IPeripheral{
    int color;
    public static final List<String> colors = Arrays.asList( "black", "red", "green", "brown", "blue", "purple", "cyan", "silver", "gray", "pink", "lime", "yellow", "lightBlue", "magenta", "orange", "white");
	@Override
	public String getType() {
		return "Light";
	}

	@Override
	public String[] getMethodNames() {
		return (new String[] { "setColor", "getColor"});
	}

	@Override
	public Object[] callMethod(IComputerAccess computer, ILuaContext context,
			int method, Object[] arguments) throws Exception {
		if (method == 0) {
			String colorString = arguments[0].toString();
			try {
				color = Integer.parseInt(colorString);
			} catch (NumberFormatException ex) {
				if (colors.contains(colorString.toLowerCase())) {
					color = colors.indexOf(colorString.toLowerCase());
				}
				else{throw new Exception("Invalid COLOR!");}
			}
			if (color > 16 || color < 0) {
				throw new Exception("Invalid COLOR!");
			}
            //colorChange();
            return null;
        }
        else if (method == 1)
        {
        	return (new Object[]{this.color});
        }
		return null;
	}
	
	@Override
	public void readFromNBT(NBTTagCompound par1NBTTagCompound)
	{
		super.readFromNBT(par1NBTTagCompound);
		this.color = par1NBTTagCompound.getInteger("color");
		//colorChange();
	}
	@Override
	public void writeToNBT(NBTTagCompound par1NBTTagCompound)
	{
		super.writeToNBT(par1NBTTagCompound);
		par1NBTTagCompound.setInteger("color",this.color);
	}

	@Override
	public boolean canAttachToSide(int side) {return true;}

	@Override
	public synchronized void attach(IComputerAccess computer) {}

	@Override
	public synchronized void detach(IComputerAccess computer) {}

}
