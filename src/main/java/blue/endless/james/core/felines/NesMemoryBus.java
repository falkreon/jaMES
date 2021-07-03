package blue.endless.james.core.felines;

import blue.endless.james.chip.mos6502.Cpu;
import blue.endless.james.core.felines.mapper.Mapper;
import blue.endless.james.host.Bus;
import blue.endless.james.host.ControlSet;

public class NesMemoryBus implements Bus {
	byte[] ram = new byte[0x0800]; // 2KiB (2048 Bytes) of system RAM
	Controller controller1 = new Controller();
	Controller controller2 = new Controller();
	ControlSet controls;
	boolean strobe = false;
	Cpu cpu;
	Ppu ppu;
	PpuMemoryBus ppuBus;
	Mapper mapper;
	
	public void setCpu(Cpu cpu) {
		this.cpu = cpu;
	}
	
	public void setPpu(Ppu ppu) {
		this.ppu = ppu;
	}
	
	public void setMapper(Mapper mapper) {
		this.mapper = mapper;
	}
	
	public void setControls(ControlSet controls) {
		this.controls = controls;
	}
	
	@Override
	public int read(long addr) {
		addr = addr & 0xFFFFL;
		
		int result = 0;
		//if (source instanceof Cpu) {
			
			
			if (addr<0x800) {
				result = ram[(int)addr] & 0xFF;
			} else if (addr < 0x2000) {
				result = ram[(int) (addr % 0x800)] & 0xFF;
			} else if (addr < 0x3FFF) {
				int ppuReg = (int) (addr % 8);
				
				switch(ppuReg) {
				case 0: //PPUCTRL
					return ppu.readControl() & 0xFF;
				case 1: //PPUMASK
					return ppu.readMask() & 0xFF;
				case 2: //PPUSTATUS
					result = ppu.readStatus() & 0xFF;
					return result;
				}
			} else if (addr==0x4016) {
				//JOY1
				result = controller1.read() & 0xFF;
			} else if (addr==0x4017) {
				//JOY2
				result = controller2.read() & 0xFF;
			} else {
				//All this crap is cartridge mapped
				result = 0;
			}
			
			return mapper.cpuRead(addr, result) & 0xFF;
			/*
		} else if (source instanceof Ppu) {
			
			if (addr<0x2000) {
				//$0000-$1FFF: Pattern Table 0 and 1; typically mapped to CHR ROM on the cart
				result = 0; //CHR data doesn't exist in the host system so we can only return zeroes and let the mapper override with cart data.
				
			} else if (addr<0x3000) {
				//$2000-$2FFF: Nametables 0-3; reside in vram
				long physicalAddress = mapper.ppuMirror(addr) & 0x7FF;
				result = ppu.hardwareReadRam(physicalAddress);
			} else if (addr<0x3F00) {
				//$3000 is mirrored from $2000, so subtract $1000 and get the mapper index
				long physicalAddress = mapper.ppuMirror(addr - 0x1000) & 0x800;
				result = ppu.hardwareReadRam(physicalAddress);
			} else if (addr<0x3FFF) {
				//Palette ram
				
				result = 0; //TODO: Support palette reads
			}
			
			return mapper.ppuRead(addr, result) & 0xFF;
		//} else if (source instanceof Apu) {
		//	return 0;
		} else if (source==null) {
			return 0;
		} else return 0; //We don't know this hardware, give it zeroes
		*/
	}
	
	
	@Override
	public void write(long addr, int val) {
		addr = addr & 0xFFFFL;
		
		if (addr<0x800) {
			ram[(int) addr] = (byte)val;
		} else if (addr < 0x2000) {
			ram[(int) addr % 0x800] = (byte)val;
		} else if (addr < 0x3FFF) {
			int ppuReg = (int) (addr % 8);
			
			switch(ppuReg) {
			case 0: //PPUCTRL
				
				ppu.writeControl(val);
				break;
			case 1:
				//return ppu.readMask();
				break;
			case 2:
				
				//result = ppu.readStatus();
				//System.out.println("    ->"+result);
				//return result;
				break;
			case 3:
				ppu.writeOamAddress(val);
				break;
			case 4:
				ppu.writeOamData(ppuBus, val);
				break;
			case 5:
				ppu.writeScroll(val);
				break;
			case 6:
				ppu.writePpuAddress(val);
				break;
			case 7:
				ppu.writePpuData(ppuBus, val);
				break;
			}
		} else if (addr==0x4014) {
			//OAMDMA
			//System.out.println("Initiating DMA transfer on page "+Integer.toHexString(value & 0xFF));
			int pageNumber = val & 0xFF;
			long cpuaddr = pageNumber << 8;
			//System.out.println("Initiating DMA transfer on page "+Integer.toHexString((int) cpuaddr));
			
			for(int i=0; i<256; i++) {
				//System.out.println("Transferring "+Integer.toHexString((int) (cpuaddr+i)));
				//TODO: Translate to PPU bus address? Access OAM directly?
				ppu.writeOamData(ppuBus, read(cpuaddr+i));
				
				//ppu.dmaTransfer(cpuRead(cpuaddr+i));
			}
			//System.out.println("DMA transfer complete");
			
			//TODO: FIXUP CYCLES
		} else if (addr==0x4016) {
			strobe = (val & 0x1) != 0;
			if (strobe) {
				//System.out.println("Controller strobe");
				controller1.strobe(controls);
				controller2.strobe(controls);
			}
		} else {
			//All this crap is cartridge mapped
			
		}
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
