package ds.mods.CCLights2.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import ds.mods.CCLights2.CCLights2;
import ds.mods.CCLights2.block.tileentity.TileEntityBigMonitor;

public class SimpleBigMonitorRenderingHandler implements
		ISimpleBlockRenderingHandler {
	
	TileEntityBigMonitor tile = new TileEntityBigMonitor();
	public TileEntityBigMonitorRenderer tileRender = new TileEntityBigMonitorRenderer();

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
		return CCLights2.proxy.modelID;
	}

}
