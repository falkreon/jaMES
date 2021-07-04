package blue.endless.james.chip;

import java.util.ArrayList;
import java.util.Arrays;

import blue.endless.james.host.Bus;
import blue.endless.james.host.Debug;
import blue.endless.tinyevents.impl.ConsumerEvent;

public class DmgPpu {
	/** This striking palette was created by Kirokaze (https://lospec.com/kirokaze) */
	public static final int[] PAL_KIROKAZE = { 0xFF_E2F3E4, 0xFF_94E344, 0xFF_46878F, 0xFF_332C50 };
	/** This palette by Kerrie Lake (https://lospec.com/kerrielake) puts complementary colors in shadows which really slaps */
	public static final int[] PAL_WISH = { 0xFF_8BE5FF, 0xFF_608FCF, 0xFF_7550E8, 0xFF_622E4C };
	
	private ConsumerEvent<int[]> onPresentFrame = ConsumerEvent.create();
	public Bus bus;
	private Mode mode = Mode.OAM_SEARCH;
	private int pixel = 0;
	private int scanline = 0;
	private ArrayList<Sprite> oamSearchResult = new ArrayList<>();
	private int[] screen = new int[160*144];
	
	private boolean lcdEnable = false;
	private boolean absoluteBG = true;
	private boolean useHighTilemap = false;
	//private int bgTileArea = 0x8800;
	private boolean tallSprites = false;
	private boolean enableSprites = false;
	
	
	/** Background Palette
	 * 
	 * <p>Bit:
	 * <ul>
	 *   <li>7-6: Color for index 3
	 *   <li>5-4: Color for index 2
	 *   <li>3-2: Color for index 1
	 *   <li>1-0: Color for index 0
	 * </li>
	 */
	int bgp = 0x00;
	int scx = 0;
	int scy = 0;
	int[] palette = PAL_WISH;
	
	public void clock() {
		if (!lcdEnable) return;
		pixel++;
		if (pixel>456) {
			pixel = 0;
			scanline = scanline + 1;
			
			if (scanline>153) {
				scanline = 0;
				pixel = 0;
				onPresentFrame.fire(screen);
				//System.out.println("Frame complete");
			}
		}
		
		if (pixel==0 && scanline<144) {
			//TODO: OAM search
		}
		
		//Figure out mode
		if (scanline<144) {
			if (pixel==0) mode = Mode.OAM_SEARCH;
			if (pixel==80) mode = Mode.PICTURE;
			if (pixel==80+168+(oamSearchResult.size()*10)) {
				mode = Mode.HBLANK;
			}
		} else if (scanline==144 && pixel==0) {
			mode = mode.VBLANK;
			
			//Set IF to request vblank interrupt on the CPU
			//System.out.println("writing vblank intrrupt");
			int interruptFlag = bus.read(0xFF0F);
			interruptFlag = interruptFlag | 0x01;
			bus.write(0xFF0F, interruptFlag);
			
			//System.out.println("Transitioning to VBLANK");
		}
		
		if (mode==Mode.PICTURE) {
			int truePixel = pixel-80;
			if (truePixel<160) {
				int ofs = scanline*160 + truePixel;
				if (ofs<screen.length) {
					int coarseX = (truePixel+scx) / 8;
					int coarseY = (scanline+scy) / 8;
					int fineX = (truePixel+scx) % 8;
					int fineY = (scanline+scy) % 8;
					
					int tilemapIndex = (coarseY*32) + coarseX;
					
					int tilemapStart = (useHighTilemap) ? 0x9C00 : 0x9800;
					int tileId = bus.read(tilemapStart+tilemapIndex);
					
					//int tileId = (coarseY*20) + coarseX; //256x256 field can fit 32, but we can't see them all
					int charDataStart = 0;
					if (!absoluteBG) {
						if ((tileId & 0x80)!=0) {
							charDataStart = 0x9000 + ((tileId&0x7F)*16);
						} else {
							tileId &= 0x7F;
							charDataStart = 0x8800 + ((tileId&0x7F)*16);
						}
						
					} else {
						charDataStart = 0x8000 + (tileId*16);
					}
					charDataStart += (fineY*2);
					//if (fineX==0) System.out.println("TileId: "+Integer.toHexString(tileId));
					int tileRowLo = bus.read(charDataStart);
					int tileRowHi = bus.read(charDataStart + 1);
					int tileBitLo = (tileRowLo >> (7-fineX)) & 0x01;
					int tileBitHi = (tileRowHi >> (7-fineX)) & 0x01;
					
					int tilePal = (tileBitHi << 1) | tileBitLo;
					
					int color = (bgp>>(tilePal*2)) & 0x03;
					color = palette[color];
					
					//int color = palette[tilePal]; //TODO: Map through bgp
					
					screen[ofs] = color;
					
					
					for(Sprite s : oamSearchResult) {
						if (truePixel>=s.x && truePixel<s.x+8) {
							screen[ofs] = 0xFFFF0000;
						}
					}
					
					
				}
			}
		} else if (mode==Mode.OAM_SEARCH && pixel==0) {
			oamSearchResult.clear();
			
			for(int i=0; i<40; i++) {
				int baseAddress = 0xFE00+(i*4);
				
				int yPos = bus.read(baseAddress) - 16;
				int xPos = bus.read(baseAddress+1);
				int tileIndex = bus.read(baseAddress+2);
				int flags = bus.read(baseAddress+3);
				
				//if (yPos!=-16 && yPos!=(255-16)) System.out.println("x: "+xPos+" y: "+yPos);
				
				boolean enabled = (scanline>=yPos && scanline<yPos+8);
				if (enabled) {
					oamSearchResult.add(new Sprite(xPos, yPos, tileIndex, flags));
				}
			}
			//if (oamSearchResult.size()>0) System.out.println("OAM Search revealed "+oamSearchResult.size()+" sprites");
		}
		
	}
	
	public void reset() {
		bgp = 0;
		scx = 0;
		scy = 0;
		pixel = 0;
		scanline = 0;
		mode = Mode.VBLANK;
		//TODO: LCD OFF
	}
	
	//0xFF40 : LCDC
	public void writeLcdControl(int value) {
		//System.out.println("LCDControl: 0x"+Debug.hexByte(value));
		
		boolean lastEnable = lcdEnable;
		lcdEnable = ((value & 0x80) != 0);
		
		if (lastEnable && !lcdEnable) {
			Arrays.fill(screen, 0xFF_FFFFFF);
			onPresentFrame.fire(screen);
			//System.out.println("  LCDC.7 LCD Disable");
		} else if (!lastEnable && lcdEnable) {
			//Arrays.fill(screen, palette[0]);
			//System.out.println("  LCDC.7 LCD Enable");
		} else {
			//System.out.println("  LCDC.7 No change (enable: "+lcdEnable+")");
		}
		absoluteBG = ((value & 0x10)!=0);
		//System.out.println("  LCDC.4 Absolute BG: "+absoluteBG);
		
		useHighTilemap = ((value & 0x08) != 0);
		//System.out.println("  LCDC.3 use High Tilemap: "+useHighTilemap);
		
		//System.out.println("Write LCDControl: 0x"+Integer.toHexString(value));
	}
	
	public int readLcdControl() {
		//System.out.println("Read LCDControl");
		return 0b10000011;
	}
	
	//0xFF41 : LDCS
	public void writeLcdStatus(int value) {
		//System.out.println("Write LCDStatus: 0x"+Integer.toHexString(value));
	}
	
	public int readLcdStatus() {
		int value = 0x00;
		value |= mode.ordinal();
		
		//System.out.println("Read LCDStatus: 0x"+Integer.toHexString(value));
		
		return value;
	}
	
	//0xFF42 : SCY
	public int readSCY() {
		return scy;
	}
	
	public void writeSCY(int value) {
		//System.out.println("Writing to SCY: "+value);
		scy = value & 0xFF;
	}
	
	//0xFF43 : SCX
	public int readSCX() {
		return scx;
	}
	
	public void writeSCX(int value) {
		scx = value & 0xFF;
	}
	
	//0xFF44 : LCDY
	public int readLcdY() {
		return scanline & 0xFF;
	}
	
	//0xFF47
	public int readBGP() {
		return bgp;
	}
	
	public void writeBGP(int value) {
		bgp = value & 0xFF;
		
		//System.out.println("Write to BG Palette: 0x"+Integer.toHexString(value));
		//System.out.println("  Color 0: "+((bgp >> 0) & 0x03));
		//System.out.println("  Color 1: "+((bgp >> 2) & 0x03));
		//System.out.println("  Color 2: "+((bgp >> 4) & 0x03));
		//System.out.println("  Color 3: "+((bgp >> 6) & 0x03));
		
	}
	
	public ConsumerEvent<int[]> onPresentFrame() {
		return onPresentFrame;
	}
	
	public static enum Mode {
		HBLANK, //rest of line after PICTURE. that is, 456 - 80 - PICTURE.length
		VBLANK, //after scanline 0..143, VBLANK is every pixel on each entire line from 144..153 inclusive
		OAM_SEARCH, //pixel 0..79 of the line
		PICTURE; //next ~168 + 10 per sprite on the line after OAM_SEARCH
	}
	
	public static enum ColorName {
		WHITE,
		LIGHT_GRAY,
		DARK_GRAY,
		BLACK;
	}
	
	private static class Sprite {
		int x;
		int y;
		int tile;
		int flags;
		
		public Sprite(int x, int y, int tile, int flags) {
			this.x = x;
			this.y = y;
			this.tile = tile;
			this.flags = flags;
		}
	}
}
