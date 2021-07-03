package blue.endless.james.core.felines;

public class PpuRegisterFile {
	byte ctrl;
	byte mask;
	byte status;
	
	byte oamdata;
	int scrollx;
	int scrolly;
	
	byte ppudata;
	
	long oamaddr;
	long ppuaddr;
	long dmaaddr;
	
	long frameCycle = 0L;
	long scanlinePixel = 0L;
	long scanline = 0L;
	boolean odd = true;
	
	//PPU internal registers
	long vramAddress = 0L;
	long tempAddress = 0L;
	int fineScroll = 0;
	boolean writeToggle = false; //if true, generally we're writing the high byte of something
	int addressIncrement = 1;
	
	//boolean nmiOccurred = false; //use status bit
	boolean nmiOutput = false;
}
