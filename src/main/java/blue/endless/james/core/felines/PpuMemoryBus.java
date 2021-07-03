package blue.endless.james.core.felines;

import blue.endless.james.core.felines.mapper.Mapper;
import blue.endless.james.host.Bus;

public class PpuMemoryBus implements Bus {
	private byte[] vram = new byte[0x800];
	private byte[] palette = new byte[256];
	private byte[] oam = new byte[256];
	
	Ppu ppu;
	private Mapper mapper;
	
	public void setPpu(Ppu ppu) {
		this.ppu = ppu;
	}
	
	public void setMapper(Mapper mapper) {
		this.mapper = mapper;
	}
	
	@Override
	public int read(long addr) {
		addr &= 0xFFFF;
		
		int result = 0;
		if (addr<0x2000) {
			//$0000-$1FFF: Pattern Table 0 and 1; typically mapped to CHR ROM on the cart
			result = 0; //CHR data doesn't exist in the host system so we can only return zeroes and let the mapper override with cart data.
			
		} else if (addr<0x3000) {
			//$2000-$2FFF: Nametables 0-3; reside in vram
			long physicalAddress = mapper.ppuMirror(addr) & 0x7FF;
			result = readVRam(physicalAddress);
		} else if (addr<0x3F00) {
			//$3000 is mirrored from $2000, so subtract $1000 and get the mapper index
			long physicalAddress = mapper.ppuMirror(addr - 0x1000) & 0x800;
			result = readVRam(physicalAddress);
		} else if (addr<0x3FFF) {
			//Palette ram
			int physicalAddress = (int) (addr-0x3F00) & 0xFF;
			result = palette[physicalAddress];
		}
		
		return mapper.ppuRead(addr, result) & 0xFF;
	}

	@Override
	public void write(long addr, int val) {
		addr &= 0xFFFF;
		
		if (addr < 0x2000) {
			//0..200 is CHR ROM. If the cart has CHR-RAM we want to enable writes here but those carts are rare.
		} else if (addr < 0x3000){ 
			//0x2000..0x2FFF is nametable vram. Mirroring applies
			long physicalAddress = mapper.ppuMirror(addr) & 0x7FF;
			writeVRam(physicalAddress, val & 0xFF);
			
		} else if (addr < 0x3F00) {
			//0x3000..0x3EFF is typically mirrored, but we're going to block writes to this region for now.
		} else if (addr<0xFFFF) {
			//0x3F00..0x3FFF is palette wam
			palette[(int) addr & 0xFF] = (byte) val;
			//ppu.setPalette((int)addr & 0xFF, val);
		}
	}
	
	
	private void writeVRam(long addr, int value) {
		addr = addr & 0x7FF;
		if (addr>=vram.length || addr < 0) return;
		vram[(int) addr] = (byte) (value & 0xFF);
	}
	
	private int readVRam(long addr) {
		int index = (int) (addr & 0x7FF);
		return vram[index] & 0xFF;
	}
	
	public void writeOAM(long addr, int value) {
		addr = addr & 0xFF;
		oam[(int) addr] = (byte) (value & 0xFF);
	}
	
	public int readOAM(long addr) {
		int index = (int) (addr & 0xFF);
		return oam[index] & 0xFF;
	}

	@Override
	public boolean mapsRead(long address) {
		return true;
	}

	@Override
	public boolean mapsWrite(long address) {
		return true;
	}
}
