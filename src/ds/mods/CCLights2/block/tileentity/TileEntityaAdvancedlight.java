package ds.mods.CCLights2.block.tileentity;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.tileentity.TileEntity;
import cpw.mods.fml.common.network.PacketDispatcher;
import dan200.computer.api.IComputerAccess;
import dan200.computer.api.ILuaContext;
import dan200.computer.api.IPeripheral;

public class TileEntityaAdvancedlight  extends TileEntity implements IPeripheral {
	    public float r = 255;
	    public float g = 255;
	    public float b = 255;
		private int ticksSinceSync;

	    public synchronized void detach(IComputerAccess icomputeraccess) // tried sync and without
	    {
	    }
	    
		public void attach(IComputerAccess computer) {}

	    public String getType()
	    {
	        return "Light";
	    }

	    public String[] getMethodNames()
	    {
	        return (new String[] { "setColorRGB", "getColorRGB", "getSize" });
	    }
        @Override
        public Object[] callMethod(IComputerAccess computer,ILuaContext context, int method, Object[] arguments)
	     throws Exception {
	    	if (method == 0)
	        {
	    		int r = ((Double) arguments[0]).intValue();
	    		int g = ((Double) arguments[1]).intValue();
	    		int b = ((Double) arguments[2]).intValue();
	    		if (r > 255 || r < 0 || g > 255 || g < 0 || b > 255 || b < 0)
	            {
	            	throw new Exception("Invalid RGB!");
	            }
	        	this.r = (float) r;
	            this.g = (float) g;
	            this.b = (float) b;
	            colorChange();
	            return null;
	        }
	        else if (method == 1)
	        {
	        	return (new Object[]{this.r,this.g,this.b});
	        }
	        else if (method == 2)
	        {
	        	return new Object[] {1,1};
	        }

	        return null;
	    }

	    public boolean canAttachToSide(int i)
	    {
	        return true;
	    }
	    
	    public void colorChange()
	    {
	    	ByteArrayOutputStream bos = new ByteArrayOutputStream(8);
	    	DataOutputStream outputStream = new DataOutputStream(bos);
	    	try {
	    		outputStream.writeInt(xCoord);
	    		outputStream.writeInt(yCoord);
	    		outputStream.writeInt(zCoord);
	    		outputStream.writeFloat(this.r);
	    		outputStream.writeFloat(this.g);
	    		outputStream.writeFloat(this.b);
	    	} catch (Exception ex) {
	    		ex.printStackTrace();
	    	}

	    	Packet250CustomPayload packet = new Packet250CustomPayload();
	    	packet.channel = "Light";
	    	packet.data = bos.toByteArray();
	    	packet.length = bos.size();
	    	PacketDispatcher.sendPacketToAllPlayers(packet);
	    }
	    @Override
	    public void readFromNBT(NBTTagCompound par1NBTTagCompound)
	    {
	    	super.readFromNBT(par1NBTTagCompound);
	        this.r = par1NBTTagCompound.getFloat("r");
	        this.g = par1NBTTagCompound.getFloat("g");
	        this.b = par1NBTTagCompound.getFloat("b");
	        colorChange();
	    }
	    @Override
	    public void writeToNBT(NBTTagCompound par1NBTTagCompound)
	    {
	    	super.writeToNBT(par1NBTTagCompound);
	    	par1NBTTagCompound.setFloat("r",this.r);
	    	par1NBTTagCompound.setFloat("g",this.g);
	    	par1NBTTagCompound.setFloat("b",this.b);
	    }
	    @Override
	    public void updateEntity() {
	    	if ((++ticksSinceSync % 20) * 4 == 0) {
	    		colorChange();
	    	}
	    }
}
