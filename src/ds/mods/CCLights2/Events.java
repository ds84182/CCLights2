package ds.mods.CCLights2;

import net.minecraftforge.event.Event.Result;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import ds.mods.CCLights2.item.ItemTablet;

public class Events {
	@ForgeSubscribe
	public void playerInteract(PlayerInteractEvent event)
	{
		if (event.entityPlayer.getHeldItem() != null && event.entityPlayer.getHeldItem().getItem() instanceof ItemTablet)
		{
			if (event.entityPlayer.worldObj.getBlockId(event.x, event.y, event.z) == CCLights2.gpu.blockID)
			{
				event.useBlock = Result.DENY;
				event.useItem = Result.ALLOW;
			}
			else if (event.entityPlayer.getHeldItem().getTagCompound() != null && event.entityPlayer.getHeldItem().getTagCompound().getBoolean("canDisplay"))
			{
				event.useBlock = Result.DENY;
				event.useItem = Result.ALLOW;
			}
			else
			{
				event.useBlock = Result.ALLOW;
			}
		}
	}
}
