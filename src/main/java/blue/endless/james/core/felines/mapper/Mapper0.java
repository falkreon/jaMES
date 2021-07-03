package blue.endless.james.core.felines.mapper;

import java.util.ArrayList;

public class Mapper0 implements Mapper {
	private ArrayList<byte[]> prgBanks = new ArrayList<>();
	private ArrayList<byte[]> chrBanks = new ArrayList<>();
	private byte[] saveRam = new byte[0x1000];
	private Mirror mirror;
	
	@Override
	public int cpuRead(long addr, int value) {
		if (addr<0x2000) {
			//Host memory
			return value;
		} else if (addr<0x4000) {
			//PPU Registers
			return 0;
		} else if (addr<0x4020) {
			//APU, I/O, and CPU Test Mode
			return 0;
		} else if (addr<0x6000) {
			//No comment
			return value;
		} else if (addr<0x7FFF) {
			//Save RAM
			long saveAddress = addr - 0x6000;
			return saveRam[(int) saveAddress % saveRam.length];
		} else if (addr < 0xBFFF) {
			//16KiB PRG ROM bank 0
			long romAddr = addr - 0x8000;
			return prgBanks.get(0)[(int) romAddr & 0x0FFF];
		} else if (addr <= 0xFFFF) {
			long romAddr = addr - 0xC000;
			byte[] bank = prgBanks.get(prgBanks.size()-1);
			return bank[(int) (romAddr % bank.length)];
			//return prgBanks.get(prgBanks.size()-1)[(int) romAddr % ];
		}
		
		return value;
	}

	@Override
	public boolean cpuWrite(long addr, int value) {
		if (addr < 0x2000) {
			//Host memory
			return false;
		} else if (addr<0x4000) {
			//PPU Registers
			return true; //TODO: MAP
		} else if (addr<0x4020) {
			//APU, I/O, and CPU Test Mode
			return true; //TODO: Make an Apu
		} else if (addr<0x6000) {
			//No comment
			return false;
		} else if (addr<0x7FFF) {
			//System.out.println("Writing "+Integer.toHexString(value)+" to "+Integer.toHexString((int) addr));
			//Save RAM
			long saveAddress = (addr - 0x6000) % 4096;
			saveRam[(int) saveAddress] = (byte)value;
			return true;
		} else {
			//This is the bulk ROM area. Writing to this will have no effect on Mapper 0.
			return true;
		}
	}

	@Override
	public int ppuRead(long addr, int value) {
		if (addr<0) return 0;
		if (addr<0x1000) {
			//Pattern table 0
			byte[] bank = chrBanks.get(0);
			//System.out.println("Addr: "+addr);
			return bank[(int) (addr % bank.length)] & 0xFF;
		} else if (addr<0x2000) {
			//because of a quirk of how the ines format works, this is still "bank 0"
			byte[] bank = chrBanks.get(0);
			return bank[(int) (addr % bank.length)] & 0xFF;
		} else {
			//2000-2FBF : nametables (VRAM)
			//vram which is external to the mapper.
			return value;
		}
	}

	@Override
	public long ppuWrite(long addr) {
		return -1; //No override
	}

	public void addPrgBank(byte[] prgBank) {
		prgBanks.add(prgBank);
		System.out.println("PostAddState: "+toString());
	}

	public void addChrBank(byte[] chrBank) {
		chrBanks.add(chrBank);
	}
	
	public void setMirror(Mirror mirror) {
		this.mirror = mirror;
	}
	
	@Override
	public long ppuMirror(long addr) {
		if (mirror==null) {
			return addr % 0x800;
		} else {
			return mirror.map(addr);
		}
	}
	
	public byte[] getSaveRam() {
		return saveRam;
	}
	
	/*
	@Override
	public int physicalRead(long addr) {
		if (addr<0) throw new IllegalArgumentException(); //probably hang the *virtual* machine or wrap it, but for now, throw.
		
		if (addr<=0x0FFFL) {
			//System.out.println("Accessing CHR bank 0 at "+Integer.toHexString((int) addr));
			return chrBanks.get(0)[(int) addr] & 0xFF;
		} else if (addr<=0x1FFFL) {
			//System.out.println("Accessing CHR bank 1 at "+Integer.toHexString((int) addr));
			return chrBanks.get(chrBanks.size()-1)[(int) (addr - 0x1000L)] & 0xFF;
		}
		
		if (addr<0x8000) return -1; //No data
		
		if (prgBanks.size()==0) throw new IllegalStateException();
		
		if (prgBanks.size()==1) {
			//NROM-128, mirrored
			long physicalAddr = (addr - 0x8000) % 0x4000;
			return ((int) prgBanks.get(0)[(int)physicalAddr]) & 0xFF;
		} else {
			//NROM-256
			long physicalAddr = (addr - 0x8000) % 0x8000;
			return ((int) prgBanks.get(0)[(int)physicalAddr]) & 0xFF;
		}
	}
	
	@Override
	public int physicalWrite(long addr, int val) {
		return -1; //This mapper does not config
	}*/
	
	@Override
	public String toString() {
		return "PrgBanks: "+prgBanks.size()+", ChrBanks: "+chrBanks.size();
	}
}
