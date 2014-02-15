/*
** Copyright 2005 Huxtable.com. All rights reserved.
*/

package ds.mods.CCLights2.jhlabs.image;

import java.awt.image.BufferedImage;

/**
 * A filter which subtracts Gaussian blur from an image, sharpening it.
 * @author Jerry Huxtable
 */
public class UnsharpFilter extends GaussianFilter {

	static final long serialVersionUID = 5377089073023183684L;

	private float amount = 0.5f;
	private int threshold = 1;
	
	public UnsharpFilter() {
		radius = 2;
	}
	
	public void setThreshold( int threshold ) {
		this.threshold = threshold;
	}
	
	public int getThreshold() {
		return threshold;
	}
	
	public void setAmount( float amount ) {
		this.amount = amount;
	}
	
	public float getAmount() {
		return amount;
	}
	
    @Override
	public BufferedImage filter( BufferedImage src, BufferedImage dst ) {
        int width = src.getWidth();
        int height = src.getHeight();

        if ( dst == null )
            dst = createCompatibleDestImage( src, null );

        int[] inPixels = new int[width*height];
        int[] outPixels = new int[width*height];
        src.getRGB( 0, 0, width, height, inPixels, 0, width );

		convolveAndTranspose(kernel, inPixels, outPixels, width, height, alpha, CLAMP_EDGES);
		convolveAndTranspose(kernel, outPixels, inPixels, height, width, alpha, CLAMP_EDGES);

        src.getRGB( 0, 0, width, height, outPixels, 0, width );

		float a = 4*amount;

		int index = 0;
		for ( int y = 0; y < height; y++ ) {
			for ( int x = 0; x < width; x++ ) {
				int rgb1 = outPixels[index];
				int r1 = (rgb1 >> 16) & 0xff;
				int g1 = (rgb1 >> 8) & 0xff;
				int b1 = rgb1 & 0xff;

				int rgb2 = inPixels[index];
				int r2 = (rgb2 >> 16) & 0xff;
				int g2 = (rgb2 >> 8) & 0xff;
				int b2 = rgb2 & 0xff;

				if ( Math.abs( r1 -  r2 ) >= threshold )
					r1 = PixelUtils.clamp( (int)((a+1) * (r1-r2) + r2) );
				if ( Math.abs( g1 -  g2 ) >= threshold )
					g1 = PixelUtils.clamp( (int)((a+1) * (g1-g2) + g2) );
				if ( Math.abs( b1 -  b2 ) >= threshold )
					b1 = PixelUtils.clamp( (int)((a+1) * (b1-b2) + b2) );

				inPixels[index] = (rgb1 & 0xff000000) | (r1 << 16) | (g1 << 8) | b1;
				index++;
			}
		}

        dst.setRGB( 0, 0, width, height, inPixels, 0, width );
        return dst;
    }

	@Override
	public String toString() {
		return "Blur/Unsharp Mask...";
	}
}
