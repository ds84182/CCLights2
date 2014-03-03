package ds.mods.CCLights2.gpu.imageLoader;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

public class GeneralImageLoader implements IImageLoader {

	@Override
	public BufferedImage loadImage(byte[] data) {
		try {
			return ImageIO.read(new ByteArrayInputStream(data));
		} catch (IOException e) {
			return null;
		}
	}

}
