package ds.mods.CCLights2;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;

public class Compat {

	public static void init() {
		boolean gpu = false, monitor = false, monitorBig = false, light = false, advancedlight = false, ttrans = false, ram = false, tablet = false;
		

		

		

		

		if (Config.Vanilla) {
			Compat.recipes.Vanilla(gpu, monitor, monitorBig, light,
					advancedlight, ttrans, ram, tablet);
		}
		if (Loader.isModLoaded("IC2") && Config.IC2) {
			Compat.recipes.IC2(gpu, monitor, monitorBig, light, advancedlight,
					ttrans, ram, tablet);
		}
	}

	private static class recipes {
		 static void Vanilla(boolean gpu, boolean monitor,
				boolean monitorBig, boolean light, boolean advancedlight,
				boolean ttrans, boolean ram, boolean tablet) {

			if (gpu) {
				GameRegistry.addRecipe(new ItemStack(CCLights2.gpu, 1),
						new Object[] { "III", "RGR", "GGG", 'I',
								Item.ingotIron, 'R', Item.redstone, 'G',
								Item.ingotGold });
			}
			if (monitor) {
				GameRegistry.addRecipe(new ItemStack(CCLights2.monitor, 2),
						new Object[] { "III", "RLR", "GGG", 'I',
								Item.ingotIron, 'R', Item.redstone, 'G',
								Item.ingotGold, 'L', Block.thinGlass });
			}
			if (monitorBig) {
				GameRegistry.addRecipe(new ItemStack(CCLights2.monitorBig, 8),
						new Object[] { "LLL", "LGL", "LLL", 'G',
								CCLights2.monitor, 'L', Block.thinGlass });
			}
			if (ttrans) {
				GameRegistry.addRecipe(new ItemStack(CCLights2.ttrans, 1),
						new Object[] { " L ", "LGL", " L ", 'G',
								CCLights2.monitor, 'L', Item.redstone });
			}
			if (ram) {
				GameRegistry.addRecipe(new ItemStack(CCLights2.ram, 8),
						new Object[] { "III", "R R", "GGG", 'I',
								Item.ingotIron, 'R', Block.blockRedstone, 'G',
								Item.ingotGold, 'L', Block.thinGlass });
				// register recipes for RAM upgrades item,output,metadata
				for (int i = 0; i < 8; i++) {
					for (int x = 0; x < 8; x++) {
						int total = i + x;
						if (total <= 8 && i != total && x != total) {
							CCLights2.debug(i + "+" + x + "=" + total);
							GameRegistry.addShapelessRecipe(new ItemStack(
									CCLights2.ram, 1, total + 1),
									new ItemStack(CCLights2.ram, 1, i),
									new ItemStack(CCLights2.ram, 1, x));
						}
					}
				}
			}
			if (tablet) {
				GameRegistry.addRecipe(new ItemStack(CCLights2.tablet, 2),
						new Object[] { "GIG", "RMR", "GIG", 'I',
								Item.ingotIron, 'R', Item.redstone, 'G',
								Item.ingotGold, 'M', CCLights2.monitorBig });
			}
		}

		static void IC2(boolean gpu, boolean monitor,boolean monitorBig, boolean light, boolean advancedlight,boolean ttrans, boolean ram, boolean tablet) {
			// do some stuff to fak over recipes here kthxbai
		}
	}
}
