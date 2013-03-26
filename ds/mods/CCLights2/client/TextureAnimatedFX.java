package ds.mods.CCLights2.client;

import java.io.IOException;

import net.minecraft.client.renderer.RenderEngine;
import net.minecraft.src.ModTextureAnimation;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.client.TextureFXManager;

public class TextureAnimatedFX extends ModTextureAnimation
{
	public TextureAnimatedFX(String texture, int index) throws IOException
	{
		super(index, 1, texture, TextureFXManager.instance().loadImageFromTexturePack(FMLClientHandler.instance().getClient().renderEngine, texture), 1);
	}

	@Override
    public void bindImage(RenderEngine renderengine)
    {
    	//Binds texture with GL11 to use specific icon index.
        GL11.glBindTexture(3553 /*GL_TEXTURE_2D*/, renderengine.getTexture("/ds/mods/CCLights2/texture/terrain.png"));
    }
}
