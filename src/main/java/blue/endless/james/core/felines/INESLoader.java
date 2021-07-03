package blue.endless.james.core.felines;

import java.io.IOException;
import java.io.InputStream;

import blue.endless.james.core.felines.mapper.Mapper0;
import blue.endless.james.core.felines.mapper.Mirror;

public class INESLoader {
	public static final int SIXTEEN_K = 0x4000;
	public static final int EIGHT_K   = 0x2000;
	
	//Flags6
	public static final int FLAG_FOUR_SCRN = 0b1000;
	public static final int FLAG_TRAINER   = 0b0100;
	public static final int FLAG_SRAM      = 0b0010;
	public static final int FLAG_V_MIRROR  = 0b0001;
	
	public static final int FLAG_PLAYCHOICE_10 = 0b0010;
	public static final int FLAG_VS_UNISYSTEM = 0b0001;
	
	public static void loadCartridge(InputStream in, NesCore bus) throws IOException {
		assertByte('N', in.read());
		assertByte('E', in.read());
		assertByte('S', in.read());
		assertByte(0x1A, in.read());
		
		int numPrg = in.read();
		int numChr = in.read();
		int flags6 = in.read();
		boolean verticalMirror   = flag(flags6, FLAG_V_MIRROR);
		boolean hasSaveRam       = flag(flags6, FLAG_SRAM);
		boolean hasTrainer       = flag(flags6, FLAG_TRAINER);
		boolean fourScreenMirror = flag(flags6, FLAG_FOUR_SCRN);
		int mapperNumber = (flags6 >>> 4) & 0xF;
		
		int flags7 = in.read();
		mapperNumber |= (flags7 & 0xF0);
		int inesVersion = (flags7 >> 2) & 0x03;
		
		int prgRamSize = 1; //8KiB
		
		if (inesVersion==2) {
			throw new UnsupportedOperationException();
			
		} else {
			int flags8 = in.read();
			int flags9 = in.read();
			int flags10 = in.read();
			//Later we can do interesting things here but for now, don't.
			
			int junk11 = in.read();
			int junk12 = in.read();
			int junk13 = in.read();
			int junk14 = in.read();
			int junk15 = in.read();
			
			Mapper0 mapper = new Mapper0();
			mapper.setMirror(verticalMirror ? Mirror.VERTICAL : Mirror.HORIZONTAL);
			if (hasTrainer) in.skip(512);
			for(int curPrg=0; curPrg<numPrg; curPrg++) {
				byte[] prgRom = new byte[SIXTEEN_K];
				for(int i=0; i<prgRom.length; i++) {
					prgRom[i] = readByte(in);
				}
				System.out.println("Adding prg");
				mapper.addPrgBank(prgRom);
			}
			for(int curChr = 0; curChr<numChr; curChr++) {
				byte[] chrBank = new byte[EIGHT_K];
				for(int i=0; i<chrBank.length; i++) {
					chrBank[i] = readByte(in);
				}
				System.out.println("Adding chr");
				mapper.addChrBank(chrBank);
			}
			
			bus.setMapper(mapper);
		
		}
		
		System.out.println("iNES Version: "+inesVersion);
		System.out.println("Mapper #: "+mapperNumber);
		System.out.println("Num PRG banks: "+numPrg+" ("+(numPrg*SIXTEEN_K)+"KiB)");
		System.out.println("Num CHR banks: "+numChr+" ("+(numChr*EIGHT_K)+"KiB)");
		
		System.out.println("VerticalMirror: "+verticalMirror+", HasSaveRam: "+hasSaveRam+", HasTrainer: "+hasTrainer+", 4ScreenMirror: "+fourScreenMirror);
	}
	
	private static void assertByte(int expected, int actual) throws IOException {
		if (actual==-1) throw new IOException("EOF");
		if (expected!=actual) throw new IOException();
	}
	
	private static byte readByte(InputStream in) throws IOException {
		int result = in.read();
		if (result==-1) throw new IOException("EOF");
		return (byte) result;
	}
	
	private static boolean flag(int field, int flag) {
		return (field & flag) != 0;
	}
}
