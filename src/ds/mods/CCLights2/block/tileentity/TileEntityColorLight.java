package ds.mods.CCLights2.block.tileentity;

import java.util.Arrays;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import dan200.computer.api.IComputerAccess;
import dan200.computer.api.ILuaContext;
import dan200.computer.api.IPeripheral;

public class TileEntityColorLight  extends TileEntity implements IPeripheral{
    int color;
    public static final List<String> colors = Arrays.asList( "black", "red", "green", "brown", "blue", "purple", "cyan", "silver", "gray", "pink", "lime", "yellow", "lightBlue", "magenta", "orange", "white");
	@Override
	public String getType() {
		return "CCLIGHT";
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
				if (colors.contains(colorString)) {
					color = colors.indexOf(colorString);
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
	public boolean canAttachToSide(int side) {return true;}

	@Override
	public synchronized void attach(IComputerAccess computer) {}

	@Override
	public synchronized void detach(IComputerAccess computer) {}

}
