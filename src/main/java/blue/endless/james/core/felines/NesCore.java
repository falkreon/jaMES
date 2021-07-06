package blue.endless.james.core.felines;

import blue.endless.james.chip.mos6502.Cpu;
import blue.endless.james.core.felines.mapper.Mapper;
import blue.endless.james.host.ControlSet;
import blue.endless.james.host.Core;
import blue.endless.tinyevents.impl.ConsumerEvent;

public class NesCore implements Core {
	private ConsumerEvent<int[]> onPresentFrame = ConsumerEvent.create();
	Cpu cpu = new Cpu();
	Ppu ppu = new Ppu();
	NesMemoryBus bus = new NesMemoryBus();
	PpuMemoryBus ppuBus = new PpuMemoryBus();
	
	public NesCore() {
		bus.cpu = cpu;
		bus.ppu = ppu;
		bus.ppuBus = ppuBus;
		
		ppuBus.setPpu(ppu);
	}
	
	@Override
	public void hardReset() {
		cpu.hardReset();
		ppu.hardReset();
		bus.strobe = false;
	}
	
	@Override
	public void softReset() {
		cpu.softReset();
		ppu.softReset();
		bus.strobe = false;
	}
	
	@Override
	public int clock() {
		int cycles = cpu.cycle(bus);
		int ppuCycles = cycles*3;
		for(int i=0; i<ppuCycles; i++) {
			ppu.cycle(this, ppuBus);
		}
		return cycles;
	}
	
	public void setMapper(Mapper mapper) {
		bus.setMapper(mapper);
		ppuBus.setMapper(mapper);
	}
	
	/*
	public int ppuRead(long addr) {
		addr = addr & 0xFFFFL;
		
		int result = 0;
		
		
		
		//Memory map
		if (addr<0x2000) {
			//$0000-$1FFF: Pattern Table 0 and 1; typically mapped to CHR ROM on the cart
			result = 0; //CHR data doesn't exist in the host system so we can only return zeroes and let the mapper override with cart data.
			
		} else if (addr<0x3000) {
			//$2000-$2FFF: Nametables 0-3; reside in vram
			long physicalAddress = mapper.ppuMirror(addr) & 0x7FF;
			//System.out.println("Mapper address: "+Integer.toHexString((int)addr)+", Mirrored: "+physicalAddress);
			//result = vram[(int)physicalAddress];
			result = ppu.hardwareReadRam(physicalAddress);
		} else if (addr<0x3F00) {
			//$3000 is mirrored from $2000, so subtract $1000 and get the mapper index
			long physicalAddress = mapper.ppuMirror(addr - 0x1000) & 0x800;
			//result = vram[(int)physicalAddress];
			result = ppu.hardwareReadRam(physicalAddress);
		} else if (addr<0x3FFF) {
			//Palette ram
			
			
		}
		
		return mapper.ppuRead(addr, result) & 0xFF;
	}*/
	/*
	public void ppuWrite(long addr, int value) {
		addr = addr & 0xFFFFL;
		
		if (addr < 0x2000) {
			//0..200 is CHR ROM. If the cart has CHR-RAM we want to enable writes here but those carts are rare.
		} else if (addr < 0x3000){ 
			//0x2000..0x2FFF is nametable vram. Mirroring applies
			long physicalAddress = mapper.ppuMirror(addr) & 0x7FF;
			ppu.hardwareWriteRam(physicalAddress, value & 0xFF);
			
		} else if (addr < 0x3F00) {
			//0x3000..0x3EFF is typically mirrored, but we're going to block writes to this region for now.
		} else if (addr<0xFFFF) {
			//0x3F00..0x3FFF is palette wam
			ppu.setPalette((int)addr & 0xFF, value);
		}
		
		
		//addr = addr & 0xFFFFL;
		
		//long physAddr = mapper.ppuWrite(addr);
		//if (physAddr==-1) {
		//	if (addr>=vram.length) return;
		//	vram[(int)addr] = (byte)value;
		//} else {
			//mapper.physicalWrite(physAddr, value);
		//}
	}*/
	
	public void printState() {
		//System.out.println("CPU state: "+cpu.toString());
		//System.out.println("Mapper: "+mapper.toString());
	}

	@Override
	public ConsumerEvent<int[]> onPresentFrame() {
		return onPresentFrame;
	}

	@Override
	public int getFrameWidth() {
		return 280;
	}

	@Override
	public int getFrameHeight() {
		return 240;
	}

	public boolean isStopped() {
		return cpu.isHung();
	}
	
	/*
	public void printStackTrace() {
		CallFrame[] stackTrace = cpu.getRegisters().stackTrace();
		for(int i=0; i<stackTrace.length; i++) {
			System.out.println("    "+stackTrace[i].toString());
		}
		System.out.println("Stack trace complete ("+stackTrace.length+" elements)");
		//System.out.println("Save RAM: "+Arrays.toString(((Mapper0)mapper).getSaveRam()));
		byte[] saveRam = ((Mapper0) mapper).getSaveRam();
		for(int i=0; i<saveRam.length/4; i+= 24) {
			System.out.println( hexLine(saveRam, i, 16));
		}
	}*/
	
	private static String hexLine(byte[] b, int ofs, int len) {
		StringBuilder result = new StringBuilder();
		for(int i=0; i<len; i++) {
			if (ofs+i>=b.length) {
				result.append(".. ");
			} else {
				String cell = Integer.toHexString(b[ofs+i]);
				if (cell.length()<2) result.append('0');
				result.append(cell);
				result.append(' ');
			}
		}
		
		for(int i=0; i<len; i++) {
			if (ofs+i>=b.length) {
				result.append('.');
			} else {
				result.append(safeChar(b[ofs+i]));
			}
		}
		
		return result.toString();
	}
	
	@Override
	public void connectControls(ControlSet controls) {
		this.bus.controls = controls;
	}
	
	private static char safeChar(byte val) {
		if (val<0x20 || val==0x7F || val>=252) return '?';
		return (char) (val & 0xFF);
	}
	
	public void triggerNMI() {
		cpu.triggerNMI(bus);
	}
	
	public Ppu getPpu() {
		return ppu;
	}

	public void addDebugLines(int i) {
		cpu.getRegisters().addDebugLines(i);
	}
	
	public ConsumerEvent<String> onInstruction() {
		return cpu.onInstruction();
	}
	
	public void setStopped(boolean stopped) {
		if (stopped) cpu.getRegisters().hang();
	}
	
	@Override
	public void connectBios(byte[] bios) {
		
	}

	@Override
	public double getRefreshRate() {
		return 59.826;
	}

	@Override
	public double getClockSpeed() {
		//The system master clock is 236,250,000Hz crystal divided by 11 == 21,477,272.7 Hz
		
		//The CPU clock is that 21,477,272.7Hz master clock divided by 12 == 1789772.7 Hz
		
		return 1789773;
	}
}
