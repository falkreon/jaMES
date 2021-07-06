package blue.endless.james.drawing;

import java.util.Arrays;

/**
 * Contains raw sRGB image data, packed as 32-bit ARGB integers. This is the same color format as Swing and Minecraft,
 * so color math used from these places
 */
public class ImageData {
	public static final double SRGB_GAMMA = 2.4;
	
	public static final int COLOR_NONE  = 0x00_000000;
	public static final int COLOR_BLACK = 0xFF_000000;
	public static final int COLOR_RED   = 0xFF_FF0000;
	public static final int COLOR_GREEN = 0xFF_00FF00;
	public static final int COLOR_BLUE  = 0xFF_0000FF;
	
	private final int width;
	private final int height;
	private final int[] data;
	
	public ImageData(int width, int height) {
		this.width = width;
		this.height = height;
		this.data = new int[width*height];
	}
	
	public void setPixel(int x, int y, int argb) {
		if (x>=0 && x<width && y>=0 && y<width) {
			data[y*width+x] = argb;
		}
	}
	
	public int getPixel(int x, int y) {
		if (x>=0 && x<width && y>=0 && y<width) {
			return data[y*width+x];
		} else {
			return COLOR_NONE;
		}
	}
	
	public void paintPixel(int x, int y, int argb) {
		if (x>=0 && x<width && y>=0 && y<width) {
			int ofs = y*width+x;
			
			int dest = data[ofs];
			
			data[y*width+x] = mix(argb, dest, 1.0);
		}
	}
	
	public void paintImage(ImageData data, int destX, int destY, int srcX, int srcY, int width, int height) {
		for(int yi=0; yi<height; yi++) {
			for(int xi=0; xi<width; xi++) {
				paintPixel(destX+xi, destY+yi, data.getPixel(xi+srcX, yi+srcY));
			}
		}
	}
	
	public void clear() {
		Arrays.fill(data, COLOR_NONE);
	}
	
	public void clear(int argb) {
		Arrays.fill(data, argb);
	}
	
	public int getWidth() {
		return width;
	}
	
	public int getHeight() {
		return height;
	}
	
	public int[] getRawData() {
		return data;
	}
	
	/*
	 * Takes a lot of logic to adequately mix colors in linear space. This is a simple implementation which can only do
	 * alpha-blended porter-duff "normal" composites. For a more complete implementation of headless graphics, see Glow.
	 */
	
	public static final int mix(int src, int dest, double alpha) {
		double sa = ((src >> 24) & 0xFF) / 255.0;
		double sr = gammaToLinear((src >> 16) & 0xFF);
		double sg = gammaToLinear((src >>  8) & 0xFF);
		double sb = gammaToLinear((src      ) & 0xFF);
		
		double da = ((dest >> 24) & 0xFF) / 255.0;
		double dr = gammaToLinear((dest >> 16) & 0xFF);
		double dg = gammaToLinear((dest >>  8) & 0xFF);
		double db = gammaToLinear((dest      ) & 0xFF);
		
		sa *= alpha;
		
		sr = clamp(sr);
		sg = clamp(sg);
		sb = clamp(sb);
		
		double outAlpha = clamp(sa + da*(1-sa));
		double r = lerp(dr, sr, sa);
		double g = lerp(dg, sg, sa);
		double b = lerp(db, sb, sa);
		
		int ir = linearToGamma(r);
		int ig = linearToGamma(g);
		int ib = linearToGamma(b);
		int ia = (int) (outAlpha * 255.0);
		
		return ia << 24 | ir << 16 | ig << 8 | ib;
	}
	
	/** Converts one color sample from the srgb colorspace in the range [0 .. 255], into linear colorspace in the range [0.0 .. 1.0] */
	public static double gammaToLinear(int srgbElement) {
		return gammaToLinear(srgbElement, SRGB_GAMMA);
	}
	
	/** Converts one color sample from an srgb-like gamma-weighted colorspace in the range [0 .. 255], into linear colorspace in the range [0.0 .. 1.0] */
	public static double gammaToLinear(int gammaElement, double gamma) {
		if (gammaElement<0) return 0.0;
		
		double srgb = gammaElement / 255.0;
		if (srgb <= 0.04045) {
			return srgb / 12.92;
		} else if (srgb <= 1.0) {
			return Math.pow((srgb + 0.055) / 1.055, gamma);
		} else {
			return 1.0;
		}
	}
	
	/** Converts one color sample from linear colorspace in the range [0.0 .. 1.0] into the srgb colorspace in the range [0 .. 255] */
	public static int linearToGamma(double linearElement) {
		return linearToGamma(linearElement, SRGB_GAMMA);
	}
	
	/** Converts one color sample from linear colorspace in the range [0.0 .. 1.0] into an srgb-like gamma-weighted colorspace in the range [0 .. 255] */
	public static int linearToGamma(double linearElement, double gamma) {
		if (linearElement<0) {
			return 0;
		} else if (linearElement <= 0.0031308) {
			double gammaCorrected = linearElement * 12.92;
			return (int) (gammaCorrected * 255.0);
		} else if (linearElement <= 1.0) {
			double gammaCorrected = 1.055 * Math.pow(linearElement, 1.0 / gamma) - 0.055;
			return (int) (gammaCorrected * 255.0);
		} else {
			return 0xFF;
		}
	}
	
	public static double lerp(double a, double b, double t) {
		return a*(1-t) + b*t;
	}
	
	public static double clamp(double value) {
		if (value<0.0) return 0.0;
		if (value>1.0) return 1.0;
		return value;
	}
	
	public static double clamp(double value, double min, double max) {
		if (value<min) return min;
		if (value>max) return max;
		return value;
	}
}
