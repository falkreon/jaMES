package blue.endless.james.core.felines;

import blue.endless.james.host.Bus;

public class Ppu {
	Bus bus;
	private PpuRegisterFile registers = new PpuRegisterFile();
	private int[] screen = new int[280*240];
	//private byte[] vram = new byte[0x800];
	//private byte[] palette = new byte[256];
	//private byte[] oam = new byte[256];
	private long cycleNumber = 0L;
	
	/** Colors which are displayable by the nes hardware */
	//TODO: Palette loading
	private static final int[] nesColors = new int[] {
			col( 84,  84,  84), col(  0,  30, 116), col(  8,  16, 144), col( 48,   0, 136),
			col( 68,   0, 100), col( 92,   0,  48), col( 84,   4,   0), col( 60,  24,   0),
			col( 32,  42,   0), col(  8,  58,   0), col(  0,  64,   0), col(  0,  60,   0),
			col(  0,  50,  60), col(  0,   0,   0), col(  0,   0,   0), col(  0,   0,   0),
			//                  ^^^^^^^^^^^^^^^^^^ Super Black
			col(152, 150, 152), col(  8,  76, 196), col( 48,  50, 236), col( 92,  30, 228),
			col(136,  20, 176), col(160,  20, 100), col(152,  34,  32), col(120,  60,   0),
			col( 84,  90,   0), col( 40, 114,   0), col(  8, 124,   0), col(  0, 118,  40),
			col(  0, 102, 120), col(  0,   0,   0), col(  0,   0,   0), col(  0,   0,   0),
			
			col(236, 238, 236), col( 76, 154, 236), col(120, 124, 236), col(176,  98, 236),
			col(228,  84, 236), col(236,  88, 180), col(236, 106, 100), col(212, 136,  32),
			col(160, 170,   0), col(116, 196,   0), col( 76, 208,  32), col( 56, 204, 108),
			col( 56, 180, 204), col( 60,  60,  60), col(  0,   0,   0), col(  0,   0,   0),
			
			col(236, 238, 236), col(168, 204, 236), col(188, 188, 236), col(212, 178, 236),
			col(236, 174, 236), col(236, 174, 212), col(236, 180, 176), col(228, 196, 144),
			col(204, 210, 120), col(180, 222, 120), col(168, 226, 144), col(152, 226, 180),
			col(160, 214, 228), col(160, 162, 160), col(  0,   0,   0), col(  0,   0,   0),
	};
	
	private static final int col(int r, int g, int b) {
		if (r==0 && g==0 && b==0) return 0;
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
	
	public void softReset() {
		registers.ctrl = 0;
		registers.mask = 0;
		registers.scrollx = 0;
		registers.scrolly = 0;
		registers.ppudata = 0;
		
		registers.scanline = 0;
		registers.scanlinePixel = 0;
	}
	
	public void hardReset() {
		softReset();
		registers.oamaddr = 0L;
		registers.ppuaddr = 0L;
	}
	
	public int cycle(NesCore core, PpuMemoryBus bus) {
		int addr = (int) (registers.scanline*280 + registers.scanlinePixel);
		if (addr<0 | addr>=screen.length) addr = 0;
		//screen[addr] = 0xFF2244CC;
		
		
		
		//START: process scanline pixel
		
		
		long tileX = registers.scanlinePixel / 8;
		long tileY = registers.scanline / 8;
		long tileRow = registers.scanline % 8;
		long tileColumn = registers.scanlinePixel % 8;
		//int tileId = (int) (tileY*32+tileX);
		//System.out.println("Registers.CTRL: "+Integer.toHexString(registers.ctrl & 0xFF));
		long page = (registers.ctrl >>> 4) & 1;
		
		if (tileX>=31) tileX = 0;
		if (tileY>=29) tileY = 0;
		
		//Index into the $2000..$2400 nametable 0
		//32 tiles per row
		long nameTableOffset = tileY*32+tileX + 0x000;
		int tileId = bus.read(0x2000 + nameTableOffset);
		
		
		long baseAddr = (page * 0x1000L);
		//long baseAddr = 0x1000L; //Page 1 (background tiles) //TODO: Set from control register via 'page'
		baseAddr += tileId*16+tileRow;
		
		//The following reads are flipped due to how the shift registers are shifted. I approve.
		int tileDataLow = (bus.read(baseAddr) >>> (7-tileColumn)) & 1;
		int tileDataHigh = (bus.read(baseAddr + 8) >>> (7-tileColumn)) & 1;
		int tilePalEntry = (tileDataHigh << 1) | tileDataLow;
		//tilePalEntry = (int) (Math.random()*16.0);
		//int debug = bus.ppuRead(baseAddr);
		
		
		long attrTableOffset = 0x23C0 | (tileX >> 2) | ((tileY >> 2) << 3);
		int attrByte = bus.read(attrTableOffset);
		if ((tileY & 0x02) != 0) attrByte >>= 4;
		if ((tileX & 0x02) != 0) attrByte >>= 2;
		attrByte &= 0x03;
		
		int nesColor = bus.read(0x3F00 + (attrByte*4 + tilePalEntry));
		int rgbColor = nesColors[nesColor & 0b0111_1111];
		
		//if (registers.scanline==0 && registers.scanlinePixel==0) {
			//System.out.println("TileId: "+tileId);
		//	System.out.println("OffsetAddr: "+Integer.toHexString((int) offsetAddr)+", BaseAddr: "+Integer.toHexString((int) baseAddr));
		//	System.out.println("rawPage: "+registers.ctrl);
		//	System.out.println("tilex: "+tileX+", tiley: "+tileY+", tileRow: "+tileRow+", tileColumn: "+tileColumn+", page: "+page);
		//	System.out.println("    tileLow:"+tileDataLow+", tileHi: "+tileDataHigh+", tilePaletteEntry: "+tilePalEntry+", debug: "+Integer.toHexString(debug));
		//}
		if (tilePalEntry!=0) {
			screen[addr] = rgbColor;
		} else {
			nesColor = bus.read(0x3F00 + (attrByte*4 + tilePalEntry));
			screen[addr] = nesColors[nesColor & 0b0111_1111];
		}
		
		int spritePage = (registers.ctrl >>> 3) & 1;
		int spriteBaseAddr = spritePage * 0x1000;
		for(int i=0; i<64; i++) {
			int base = i*4;
			int spriteY   = bus.readOAM(base + 0);
			if (spriteY>=0xEF) continue;
			//TODO: Read OAM through PPU bus
			int tileIndex = bus.readOAM(base + 1);
			int attribs   = bus.readOAM(base + 2);
			int spriteX   = bus.readOAM(base + 3);
			
			int paletteIndex = (attribs & 0x3) + 4;
			boolean hFlip = (attribs & 0x40)!=0;
			boolean vFlip = (attribs & 0x80)!=0;
			
			if (registers.scanline>=spriteY && registers.scanline<spriteY+8) {
				if (registers.scanlinePixel>=spriteX && registers.scanlinePixel<spriteX+8) {
					int sprColumn = (int) (registers.scanlinePixel - spriteX) % 8;
					int sprRow = (int) (registers.scanline - spriteY) % 8;
					if (vFlip) sprRow = 7-sprRow;
					if (hFlip) sprColumn = 7-sprColumn;
					int curAddr = spriteBaseAddr + (tileIndex*16) + sprRow;
					
					int spriteDataLow = (bus.read(curAddr) >>> (7-sprColumn)) & 1;
					int spriteDataHigh = (bus.read(curAddr + 8) >>> (7-sprColumn)) & 1;
					int spritePalEntry = (spriteDataHigh << 1) | spriteDataLow;
					
					if (spritePalEntry>0) {
						int spriteNesColor = bus.read(0x3F00 + (paletteIndex*4 + spritePalEntry));
						//int spriteNesColor = palette[paletteIndex*4 + spritePalEntry];
						int spriteRgbColor = nesColors[spriteNesColor & 0b0111_1111];
						screen[addr] = spriteRgbColor;
					}
					
				}
				
			}
		}
		
		//END: process scanline pixel
		
		
		
		
		if (registers.scanlinePixel>256) {
			//registers.oamaddr = 0L;
		}
		
		//Advance frameCycle, scanline, and scanlinePixel
		registers.frameCycle++;
		
		registers.scanlinePixel++;
		if (registers.scanlinePixel>340) {
			registers.scanlinePixel = 0;
			registers.scanline++;
		}
		
		if (registers.scanline==241L && registers.scanlinePixel==0) {
			System.out.println("Start vblank / PPUStatus VBL set");
			registers.status |= 0x80;
			
			//registers.ppuaddr = 0;
			if (registers.nmiOutput) {
				core.triggerNMI();
			}
			//registers.nmiOccurred = true;
		} else if (registers.scanline>=281) {
			//We're starting a new frame
			if (registers.odd) {
				registers.frameCycle = 1;
				registers.scanline = 0;
				registers.scanlinePixel = 1;
			} else {
				registers.frameCycle = 0;
				registers.scanline = 0;
				registers.scanlinePixel = 0;
			}
			//registers.nmiOccurred = false;
			registers.status &= ~0x80;
			registers.odd = !registers.odd;
			
			//System.out.println("FRAME");
			core.onPresentFrame().fire(screen);
		}
		
		
		//if (registers.scanlinePixel==1) System.out.println(registers.scanline);
		
		cycleNumber++;
		return 1;
	}
	
	

	public int readControl() {
		return registers.ctrl & 0xFF; //TODO: INCORRECT; READS FROM THIS REGISTER ARE ALWAYS STALE
	}

	public int readMask() {
		return registers.mask & 0xFF; //TODO: POSSIBLY INCORRECT
	}

	public int readStatus() {
		int result = registers.status & 0xFF;
		//System.out.println(result);
		registers.status = (byte) (registers.status & ~0x80); // (byte) (registers.status & 0x7F); //Reading status clears vblank
		System.out.println("Reading out: "+Integer.toHexString(result)+" / "+registers.status);
		registers.writeToggle = false; //Reading status resets write toggle for ppuscroll and ppuaddr regs
		return result;
	}
	
	public void writeControl(int value) {
		if ((value & 0x80) != 0) {
			if (!registers.nmiOutput) System.out.println("NMI Enable");
			registers.nmiOutput = true;
		} else {
			if (registers.nmiOutput) System.out.println("NMI Disable");
			registers.nmiOutput = false;
		}
		
		if ((value & 0x04) != 0) {
			registers.addressIncrement = 32;
		} else {
			registers.addressIncrement = 1;
		}
		
		registers.ctrl = (byte)(value & 0xFF);
	}
	
	public void writeOamAddress(int value) {
		registers.oamaddr = value & 0xFF;
	}

	public void writePpuAddress(int value) {
		if (registers.writeToggle) {
			registers.ppuaddr = registers.tempAddress | (value & 0xFF);
			registers.tempAddress = 0L;
			//System.out.println("PPUADDR updated to "+Integer.toHexString((int) registers.ppuaddr));
		} else {
			registers.tempAddress = (value & 0xFF) << 8;
			//registers.ppuaddr = (value & 0xFF) << 8;
			//registers.ppuaddr = (registers.ppuaddr & 0x00FF) | ((value & 0xFF) << 8);
		}
		registers.writeToggle = !registers.writeToggle;
	}

	public void writePpuData(Bus bus, int value) {
		registers.ppuaddr = registers.ppuaddr & 0xFFFF;
		value = value & 0xFF;
		
		bus.write(registers.ppuaddr, value);
		System.out.println("PPU $"+Integer.toHexString((int) registers.ppuaddr)+" = "+value);
		registers.ppuaddr = (registers.ppuaddr + registers.addressIncrement) & 0xFFFF;
	}
	
	public void writeOamData(PpuMemoryBus bus, int value) {
		bus.writeOAM(registers.oamaddr, value);
		registers.oamaddr = ( registers.oamaddr + 1 ) & 0xFF;
	}

	public void writeScroll(int value) {
		if (registers.writeToggle) {
			registers.scrolly = value;
		} else {
			registers.scrollx = value;
		}
		registers.writeToggle = !registers.writeToggle;
	}
	
	public long getCycleNumber() {
		return cycleNumber;
	}
}
