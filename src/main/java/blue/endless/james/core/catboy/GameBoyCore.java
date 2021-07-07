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
	private ControlSet controls = null;
	private boolean isJoypadAction = false;
	private boolean isJoypadDirection = false;
	
	public GameBoyCore() {
		cpuBus.setUnmappedValue(0xFF);
		cpuBus.map(vram, 0x8000);
		cpuBus.map(ram,  0xC000, 0x3E00); //Mirrored up to FE00, size=3E00
		cpuBus.map(oam, 0xFE00);
		
		cpuBus.map(this::readJoypad, this::writeJoypad, 0xFF00);
		cpuBus.map(this::readSerialData, this::writeSerialData, 0xFF01);
		cpuBus.map(this::writeSerialControl, 0xFF02);
		
		//TODO: Timer divider at 0xFF04?
		cpuBus.map(cpu::readTimerCounter, cpu::writeTimerCounter, 0xFF05);
		cpuBus.map(cpu::readTimerResetValue, cpu::writeTimerResetValue, 0xFF06);
		cpuBus.map(cpu::readTimerControl, cpu::writeTimerControl, 0xFF07);
		cpuBus.map(interruptFlag, 0xFF0F);
		cpuBus.map(ppu::readLcdControl, ppu::writeLcdControl, 0xFF40);
		cpuBus.map(ppu::readLcdStatus, ppu::writeLcdStatus, 0xFF41);
		cpuBus.map(ppu::readSCY, ppu::writeSCY, 0xFF42);
		cpuBus.map(ppu::readSCX, ppu::writeSCX, 0xFF43);
		cpuBus.map(ppu::readLcdY, 0xFF44);
		cpuBus.map(this::writeOamDma, 0xFF46);
		cpuBus.map(ppu::readBGP, ppu::writeBGP, 0xFF47);
		cpuBus.map(ppu::writeOBP0, 0xFF48);
		cpuBus.map(ppu::writeOBP1, 0xFF49);
		cpuBus.map(ppu::readWindowY, ppu::writeWindowY, 0xFF4A);
		cpuBus.map(ppu::readWindowX, ppu::writeWindowX, 0xFF4B);
		cpuBus.map(this::unmapBios, 0xFF50);
		cpuBus.map(hram, 0xFF80);
		
		
		
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
		this.controls = controls;
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
		if (addr<0xC000) return;
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
	
	public int readJoypad() {
		int value = 0xFF;
		if (controls!=null) {
			if (isJoypadAction) {
				if (controls.get("A")) value &= ~0x01;
				if (controls.get("B")) value &= ~0x02;
				if (controls.get("Select")) value &= ~0x04;
				if (controls.get("Start")) value &= ~0x08;
			}
			if (isJoypadDirection) {
				if (controls.get("Right")) value &= ~0x01;
				if (controls.get("Left")) value &= ~0x02;
				if (controls.get("Up")) value &= ~0x04;
				if (controls.get("Down")) value &= ~0x08;
			}
		}
		return value;
	}
	
	public void writeJoypad(int value) {
		isJoypadDirection = ((value & 0x10)==0);
		isJoypadAction = ((value & 0x20)==0);
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

	@Override
	public double getRefreshRate() {
		return 59.73;
	}

	@Override
	public double getClockSpeed() {
		return 4194304; //or 8388608 for GBC-mode
	}
}
