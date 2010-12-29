package org.mugendb;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * A simple single image format that is mainly used to store 8-bit
 * indexed-color images with light RLE compression. It can store
 * uncompressed and 24-bit images as well.
 * 
 * @author Kristopher Ives <kristopher.ives@gmail.com>
 */
public class PCX {
	/** A 128-byte header in LITTLE_ENDIAN */
	final protected ByteBuffer header;
	
	public PCX(int width, int height, boolean encoded, int bpp, int[] colormap) {
		this.header = ByteBuffer.allocate(128);
		header.order(ByteOrder.LITTLE_ENDIAN);
		header.clear();
		
		switch (bpp) {
		case 8:
		case 24:
			break;
		default:
			throw new IllegalArgumentException("Illegal Bits per Pixel");
		}
		
		header.put((byte)0x0A);
		header.put(encoded ? (byte)1 : (byte)0);
		header.put((byte)bpp);
		header.putShort((short)0);
		header.putShort((short)0);
		header.putShort((short)(width+1));
		header.putShort((short)(height+1));
		header.putShort((short)96);
		header.putShort((short)96);
		
		if (colormap != null && colormap.length <= 16) {
			for (int i=0; i < 16; i++) {
				int p = i < colormap.length ? colormap[i] : 0;
				
				header.put((byte)((p << 16) & 0xFF));
				header.put((byte)((p << 8) & 0xFF));
				header.put((byte)(p & 0xFF));
			}
		} else {
			header.put(new byte[16 * 3]);
		}
		
		int bpl = ((width / 2) * 2) * (bpp / 8);
		
		header.put((byte)1);
		header.putShort((short)bpl);
		header.putShort((short)0);
		header.putShort((short)width);
		header.putShort((short)height);
	}
	
	public PCX(ByteBuffer header, int size) {
		this.header = header.slice();
		this.header.order(ByteOrder.LITTLE_ENDIAN);
		this.header.clear();
		
		if ((header.get(0) & 0xFF) != 0x0A) {
			throw new IllegalArgumentException("Bad PCX Header");
		}
	}
	
	public void setData(ByteBuffer data) {
		
	}
	
	public String toString() {
		return String.format("PCX (%dx%d)", getWidth(), getHeight());
	}
	
	public int getVersion() {
		return header.get(1) & 0xFF;
	}
	
	public int getEncoding() {
		return header.get(2) & 0xFF;
	}
	
	public int getBitsPerPixel() {
		return header.get(3) & 0xFF;
	}
	
	public int getWindowLeft() { return header.getShort(4); }
	public int getWindowTop() { return header.getShort(6); }
	public int getWindowRight() { return header.getShort(8); }
	public int getWindowBottom() { return header.getShort(10); }
	
	public int getHorizontalDPI() {
		return header.getShort(12) & 0xFFFF;
	}
	
	public int getVerticalDPI() {
		return header.getShort(14) & 0xFFFF;
	}
	
	public int[] getColorMap() {
		int[] colors = new int[16];
		
		return colors;
	}
	
	public int getPlaneCount() {
		return header.get(65) & 0xFF;
	}
	
	public int getBytesPerLine() {
		return header.getShort(66) & 0xFFFF;
	}
	
	public int getPaletteInfo() {		
		return header.getShort(68) &0xFFFF;
	}
	
	public int getWidth() {
		return header.getShort(70) & 0xFFFF;
	}
	
	public int getHeight() {
		return header.getShort(72) & 0xFFFF;
	}
	
	public boolean getPalette(int[] colors, FileInputStream in) throws IOException {
		FileChannel c = in.getChannel();
		
		c.position(c.size() - 769);
		
		if (in.read() != 0x0C) {
			System.err.printf("Doesn't have palette\n");
			return false;
		}
		
		int r, g, b;
		int i;
		
		for (i=0; i < 256; i++) {
			r = in.read();
			g = in.read();
			b = in.read();
			
			if (r < 0 || g < 0 || b < 0) {
				break;
			}
			
			colors[i] = (r << 16) | (g << 8) | b;
		}
		
		if (i < 255) {
			System.err.printf("Bad palette only has %d entries\n", i);
			return false;
		}
		
		return true;
	}
	
	public boolean getImage(final byte[] out, FileInputStream in) throws IOException {
		FileChannel c = in.getChannel();
		c.position(128);
		
		return getImage(out, in);
	}
	
	public boolean getImage(final byte[] out, InputStream in) throws IOException {
		int width = getWidth();
		int height = getHeight();
		int bpl = getBytesPerLine();
		int pos = 0;
		
		int run, b;
		int x, y;
		
		for (y=0; y < height; y++) {
			x = 0;
			
			while (x < bpl) {
				b = in.read();
				if (b < 0) return false;
				
				if (b > 192) {
					run = b & 0x3F;
					b = in.read();
					
					while (run-- > 0) {
						if (x < width) out[pos++] = (byte)b;//out.put((byte)b);
						x++;
					}
				} else {
					// Literal
					if (x < width) out[pos++] = (byte)b;
					x++;
				}
			}
		}
		
		return true;
	}
	
	public int encode(final ByteBuffer in, final OutputStream out) throws IOException {
		int length = 0;
		int width = getWidth();
		int height = getHeight();
		int bpl = getBytesPerLine();
		//int bpp = getBitsPerPixel();
		int a, b;
		int run;
		int i;
		a = b = in.get() & 0xFF;
		
		for (int y=0; y < height; y++) {
			i = 1;
			
			for (int x=0; x < width; x++) {
				run = 1;
				i++;
				
				while (in.hasRemaining() && i < bpl && (b=in.get() & 0xFF) == a) {
					run++;
					i++;
				}
				
				if (run == 1 && a <= 192) {
					out.write((byte)a);
				} else {
					while (run > 0) {
						out.write((byte)Math.min(64, run));
						run -= 64;
					}
				}
				
				a = b;
			}
		}
		
		return length;
	}
}
