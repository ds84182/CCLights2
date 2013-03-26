package ds.mods.CCLights2.client.render;

import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import ds.mods.CCLights2.block.tileentity.MonitorBase;

public class TileEntityMonitorRenderer extends TileEntitySpecialRenderer {

	@Override
	public void renderTileEntityAt(TileEntity var1, double var2, double var4,
			double var6, float var8) {
		glPushMatrix();
		render((MonitorBase) var1);
		glPopMatrix();
	}
	
	public void render(MonitorBase m)
	{
		
	}

}
