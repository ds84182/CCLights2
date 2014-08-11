package ds.mods.CCLights2.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

public class ModelExternalMonitor extends ModelBase
{
  ModelRenderer Monitor;
  private static final ResourceLocation MonitorTextures = new ResourceLocation("cclights","textures/blocks/ExternalMonitor.png");
  
  public ModelExternalMonitor()
  {
    textureWidth = 64;
    textureHeight = 32;
    
      Monitor = new ModelRenderer(this, 0, 0);
      Monitor.addBox(0F, 0F, 0F, 16, 16, 16);
      Monitor.setRotationPoint(0F, 0F, 0F);
      Monitor.setTextureSize(64, 32);
      Monitor.mirror = true;
      setRotation(Monitor, 0F, 0F, 0F);
  }

  public void renderModel(){
	  GL11.glPushMatrix();
	  Minecraft.getMinecraft().renderEngine.bindTexture(MonitorTextures);
	  GL11.glScalef(1F, -1F, -1F);
	  GL11.glTranslatef(-0.5F, -1.5F, -0.5F);
	  Monitor.render(0.0625F);
	  GL11.glPopMatrix();
  }
  
  private void setRotation(ModelRenderer model, float x, float y, float z)
  {
    model.rotateAngleX = x;
    model.rotateAngleY = y;
    model.rotateAngleZ = z;
  }

}
