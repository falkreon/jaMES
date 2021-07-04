package blue.endless.james.core.catboy;

import java.util.Arrays;

import blue.endless.james.chip.DmgPpu;
import blue.endless.james.chip.sm83.Sm83;
import blue.endless.james.host.ControlSet;
import blue.endless.james.host.Core;
import blue.endless.james.host.Debug;
import blue.endless.james.host.MappedBus;
import blue.endless.tinyevents.impl.ConsumerEvent;

public class GameBoyCore implements Core {
	public Sm83 cpu = new Sm83();
	private DmgPpu ppu = new DmgPpu();
	private MappedBus cpuBus = new MappedBus();
	private MappedBus ppuBus = new MappedBus();
	private byte[] vram = new byte[0x2000];
	private byte[] oam = new byte[0xA0];
	private byte[] ram = new byte[0x2000];
	private byte[] bios = null;
	private byte[] hram = new byte[0xFFFE-0xFF80];
	private byte[] cart = null;
	private int serialShiftRegister = 0xFF;
	public String serialConsoleLine = "";
	private byte[] interruptEnable = new byte[1];
	private byte[] interruptFlag = new byte[1];
	
	public GameBoyCore() {
		cpuBus.setUnmappedValue(0xFF);
		cpuBus.map(vram, 0x8000);
		cpuBus.map(ram,  0xC000, 0x3E00); //Mirrored up to FE00, size=3E00
		cpuBus.map(oam, 0xFE00);
		cpuBus.map(this::readSerialData, this::writeSerialData, 0xFF01);
		cpuBus.map(this::writeSerialControl, 0xFF02);
		cpuBus.map(cpu::readTimerControl, cpu::writeTimerControl, 0xFF07);
		cpuBus.map(ppu::readLcdControl, ppu::writeLcdControl, 0xFF40);
		cpuBus.map(ppu::readLcdStatus, ppu::writeLcdStatus, 0xFF41);
		cpuBus.map(ppu::readSCY, ppu::writeSCY, 0xFF42);
		cpuBus.map(ppu::readSCX, ppu::writeSCX, 0xFF43);
		cpuBus.map(ppu::readLcdY, 0xFF44);
		
		cpuBus.map(this::writeOamDma, 0xFF46);
		cpuBus.map(ppu::readBGP, ppu::writeBGP, 0xFF47);
		cpuBus.map(this::unmapBios, 0xFF50);
		cpuBus.map(hram, 0xFF80);
		cpuBus.map(interruptFlag, 0xFF0F);
		cpuBus.map(interruptEnable, 0xFFFF);
		cpu.bus = cpuBus;
		
		//This is not *really* how things work but I'm choosing to map it this way
		ppuBus.setUnmappedValue(0xFF);
		ppuBus.map(vram, 0x8000);
		ppuBus.map(oam, 0xFE00);
		ppuBus.map(interruptFlag, 0xFF0F);
		ppuBus.map(interruptEnable, 0xFFFF);
		ppu.bus = ppuBus;
	}
	
	public int clock() {
		if (cpu.regs.stopped) System.out.println("Stopped.");
		int cycles = cpu.clock();
		for(int i=0; i<cycles*2; i++) ppu.clock();
		
		return cycles;
	}

	@Override
	public int getFrameWidth() {
		return 160;
	}

	@Override
	public int getFrameHeight() {
		return 144;
	}

	@Override
	public ConsumerEvent<int[]> onPresentFrame() {
		return ppu.onPresentFrame();
	}

	@Override
	public void softReset() {
		cpu.softReset();
		ppu.reset();
		cpuBus.priorityMap(bios, 0);
	}

	@Override
	public void hardReset() {
		Arrays.fill(ram, (byte) 0xFF);
		Arrays.fill(vram, (byte) 0xFF);
		Arrays.fill(oam, (byte) 0xFF);
		softReset();
		cpu.hardReset();
		
	}

	private void unmapBios(int value) {
		if (bios==null) return; //Can't unmap null
		
		//System.out.println("Write to UNMAP ROM: 0x"+Integer.toHexString(value));
		if (value==0x01) {
			cpuBus.unmap(bios);
			ppu.writeSCY(0); //FOR DEBUG
		}
	}
	
	@Override
	public void connectControls(ControlSet controls) {
		
	}
	
	public boolean isStopped() {
		return cpu.regs.stopped;
	}
	
	public void setStopped(boolean stop) {
		cpu.regs.stopped = stop;
	}

	@Override
	public void connectBios(byte[] bios) {
		this.bios = bios;
		//cpuBus.map(bios, 0x00);
	}
	
	public int readSerialData() {
		return serialShiftRegister & 0xFF;
	}
	
	public void writeOamDma(int value) {
		int addr = (value & 0xFF) << 8;
		addr -= 0xC000;
		addr = addr % 2000;
		
		//System.out.println("OAMDMA: "+Debug.hexShort(addr+0xC000));
		for(int i=0; i<0xA0; i++) {
			oam[i] = ram[addr+i];
		}
	}
	
	public void writeSerialData(int i) {
		serialShiftRegister = i & 0xFF;
		//System.out.println("Serial register write: 0x"+Integer.toHexString(serialShiftRegister)+" ("+((char) serialShiftRegister)+")");
	}
	
	public void writeSerialControl(int i) {
		if (i==0x81) {
			if (serialShiftRegister==0x0A) {
				System.out.println("Serial Console> "+serialConsoleLine);
				serialConsoleLine = "";
			} else {
				serialConsoleLine = serialConsoleLine + (char) serialShiftRegister;
			}
			//System.out.println("$"+Integer.toHexString((int) cpu.regs.pc)+" Serial control out: "+((char) serialShiftRegister));
			serialShiftRegister = 0xFF;
		} else {
			//System.out.println("ABNORMAL Serial control out: 0x"+Integer.toHexString(i));
		}
	}

	public void mapRom(byte[] cart) {
		cpuBus.unmapAllMappers();
		if (this.cart != null) cpuBus.unmap(this.cart);
		this.cart = cart;
		cpuBus.map(this.cart, 0x0000);
	}
	
	public void mapRom(DmgMapper mapper) {
		cpuBus.unmapAllMappers();
		if (this.cart != null) cpuBus.unmap(this.cart);
		this.cart = null;
		cpuBus.map(mapper);
	}
}
