package ds.mods.CCLights2.client.render;

import java.awt.Color;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import ds.mods.CCLights2.CommonProxy;
import ds.mods.CCLights2.block.tileentity.TileEntityExternalMonitor;

public class SimpleBigMonitorRenderingHandler implements
		ISimpleBlockRenderingHandler {
	
	TileEntityExternalMonitor tile = new TileEntityExternalMonitor();
	public TileEntityBigMonitorRenderer tileRender = new TileEntityBigMonitorRenderer();
	
	public SimpleBigMonitorRenderingHandler()
	{
		tile.mon.tex.fill(Color.blue);
		tile.mon.tex.drawText("Hello,", 0, 0, Color.white);
		tile.mon.tex.drawText("World!", 0, 9, Color.white); //Yes, the fake ass wrap trick
	}

	@Override
	public void renderInventoryBlock(Block block, int metadata, int modelID,
			RenderBlocks renderer) {
		tileRender.renderTileEntityAt(tile, 0D, -0D, 0D, 0F);
	}

	@Override
	public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z,
			Block block, int modelId, RenderBlocks renderer) {
		return false;
	}

	@Override
	public boolean shouldRender3DInInventory() {
		return true;
	}

	@Override
	public int getRenderId() {
		return CommonProxy.modelID;
	}

}
