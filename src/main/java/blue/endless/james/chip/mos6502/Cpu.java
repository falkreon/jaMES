package blue.endless.james.chip.mos6502;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import blue.endless.james.chip.mos6502.Opcode.Microcode;
import blue.endless.james.host.Bus;
import blue.endless.tinyevents.impl.ConsumerEvent;

public class Cpu {
	private LinkedList<String> instructionStream = new LinkedList<>();
	private RegisterFile registers = new RegisterFile();
	public long cycles = 0L;
	private ArrayDeque<Object> stackMirror = new ArrayDeque<>();
	private ArrayList<Breakpoint> breakpoints = new ArrayList<>();
	private ConsumerEvent<String> onInstruction = ConsumerEvent.create();
	
	public Cpu() {
		Breakpoint nmi = new Breakpoint();
		nmi.addr = 0xF1EC;
		breakpoints.add(nmi);
	}
	
	
	public int cycle(Bus bus) {
		//Breakpoint
		//if (registers.getPC()==0xFAA7) registers.addDebugLines(20);
		//if (registers.getPC()>=0xFBF2 && registers.getPC()<0xFE59) registers.addDebugLines(1);
		for(Breakpoint breakpoint : breakpoints) {
			if (breakpoint.addr==registers.getPC()) {
				//Because we have no "Pause" state, just add debug lines for now.
				registers.addDebugLines(41);
			}
		}
		
		
		if (this.isHung()) return 0;
		if (registers.getPC()<0) {
			//we hit a reset pin
			
			System.out.println("Loading to RESET vector...");
			
			long resetVector = (bus.read(0xFFFC) & 0xFF);
			resetVector|= bus.read(0xFFFD) << 8;
			resetVector &= 0xFFFF;
			registers.setPC(resetVector);
			
			//Uncomment this instead for automated nestest rom testing
			/*
			long resetVector = 0xC000;
			registers.setPC(resetVector);
			registers.addDebugLines(100);
			*/
			System.out.println("Reset Vector: "+Integer.toHexString((int)resetVector));
		}
		registers.setInstructionAddress();
		int opcode = bus.read(registers.getPC() & 0xFFFFL) & 0xFF;
		
		//INSTRUCTION STREAM TRACE
		{
			String trace = "";
			trace += "A:";
			trace += hexByte(registers.getA());
			trace += " X:";
			trace += hexByte(registers.getX());
			trace += " Y:";
			trace += hexByte(registers.getY());
			trace += " S:";
			trace += hexByte(registers.getS());
			
			trace += " P:";
			trace += (registers.isSet(StatusFlag.NEGATIVE)) ? "N" : "n";
			trace += (registers.isSet(StatusFlag.OVERFLOW)) ? "V" : "v";
			boolean u = (registers.getP() & 0x20) != 0;
			trace += (u) ? "U" : "u";
			boolean b = (registers.getP() & 0x10) != 0;
			trace += (b) ? "B" : "b";
			trace += (registers.isSet(StatusFlag.DECIMAL)) ? "D" : "d";
			trace += (registers.isSet(StatusFlag.INTERRUPT_DISABLE)) ? "I" : "i";
			trace += (registers.isSet(StatusFlag.ZERO)) ? "Z" : "z";
			trace += (registers.isSet(StatusFlag.CARRY)) ? "C" : "c";
			
			trace += "  $";
			trace += hexShort(registers.getInstructionAddress());
			trace += ":";
			for(int i=0; i<3; i++) {
				if (i<Opcode.INSTRUCTION_SIZE[opcode]) {
					trace += " ";
					trace += hexByte(bus.read((registers.getPC()+i) & 0xFFFF));
				} else {
					trace += "   ";
				}
			}
			trace += " ";
			trace += Opcode.INSTRUCTION_NAME[opcode & 0xFF];
			trace += Opcode.INSTRUCTION_TRACE[opcode & 0xFF].trace(bus, registers);
			
			onInstruction.fire(trace);
		}
		
		
		if (registers.getRemainingDebugLines()>0) {
			System.out.println(Integer.toHexString((int)registers.getPC())+": "+Integer.toHexString(opcode)+" "+Opcode.INSTRUCTION_NAME[opcode]+"    "+registers);//+"("+Integer.toHexString(opcode)+")");
			registers.decRemainingDebugLines();
		}
		
		registers.incPC();
		long l = 0L;
		int sz = Opcode.INSTRUCTION_SIZE[opcode]-1;
		if (sz==0) {
			l=-1;
		} else if (sz==1) {
			l = bus.read(registers.getPC()) & 0xFF;
		} else {
			l = bus.read(registers.getPC()) & 0xFF;
			l |= ( bus.read(registers.getPC()+1) & 0xFF ) << 8;
		}
		if (sz==0) {
			instructionStream.add(Integer.toHexString((int) registers.getInstructionAddress() & 0xFFFF)+": "+Opcode.INSTRUCTION_NAME[opcode]);
			//instructionStream[instructionStreamPtr] = Integer.toHexString((int) registers.getInstructionAddress() & 0xFFFF)+": "+Opcode.INSTRUCTION_NAME[opcode];
		} else {
			instructionStream.add(Integer.toHexString((int) registers.getInstructionAddress() & 0xFFFF)+": "+Opcode.INSTRUCTION_NAME[opcode]+" "+Integer.toHexString((int) l & 0xFFFF));
			//instructionStream[instructionStreamPtr] = Integer.toHexString((int) registers.getInstructionAddress() & 0xFFFF)+": "+Opcode.INSTRUCTION_NAME[opcode]+" "+Integer.toHexString((int) l & 0xFFFF);
		}
		if (instructionStream.size()>128) instructionStream.removeFirst();
		//instructionStreamPtr = (instructionStreamPtr + 1) % instructionStream.length;
		
		MemoryMode mode = Opcode.INSTRUCTION_MODE[opcode];
		Opcode.Microcode logic = Opcode.INSTRUCTION_LOGIC[opcode];
		int cycles = mode.prepareFetch(bus, registers);
		if (logic!=null) {
			cycles += logic.execute(bus, registers);
		} else {
			//System.out.println("Unimplemented opcode "+Opcode.INSTRUCTION_NAME[opcode]+"("+Integer.toHexString(opcode)+") at "+registers.getInstructionAddress());
		}
		
		return cycles;
	}
	
	public static void push(Bus bus, RegisterFile regs, int val) {
		//long pushAddr = (regs.getS() & 0xFFL) | 0x0100L;
		bus.write(regs.getS()+0x0100L, val);
		regs.offsetStackPointer(-1);
		
		//stackMirror.push(Byte.valueOf((byte)val));
	}
	
	public static void pushAddress(Bus bus, RegisterFile regs, long addr) {
		addr = addr & 0xFFFF;
		
		int low  = (int) (addr & 0xFFL);
		int high = (int)((addr >>> 8) & 0xFFL);
		
		bus.write(regs.getS()+0x0100L, low);
		regs.offsetStackPointer(-1);
		bus.write(regs.getS()+0x0100L, high);
		regs.offsetStackPointer(-1);
		
		//stackMirror.push(Long.valueOf(addr));
	}
	
	public static int pop(Bus bus, RegisterFile regs) {
		//Object o = stackMirror.pop();
		
		regs.offsetStackPointer(1);
		//long popAddr = (regs.getS() & 0xFFL) | 0x0100L;
		int result = bus.read(regs.getS() + 0x0100L) & 0xFF;
		
		return result;
	}
	
	public static long popAddress(Bus bus, RegisterFile regs) {
		//Object o = stackMirror.pop();
		
		regs.offsetStackPointer(1);
		//long popAddr = (regs.getS() & 0xFFL) | 0x0100L;
		long high = bus.read(regs.getS() + 0x0100L) & 0xFF;
		regs.offsetStackPointer(1);
		long low  = bus.read(regs.getS() + 0x0100L) & 0xFF;
		
		
		long addr = (high << 8) | low;
		
		//return ((Long)o).longValue();
		return addr;
	}
	
	public void softReset() {
		System.out.println("SoftReset");
		// a, x, y are unchanged
		
		//Throw the PC to -1 so the next instruction prefetches the interrupt vector
		registers.setPC(-1);
		
		registers.setP(0);
		registers.set(StatusFlag.INTERRUPT_DISABLE);
		registers.clear(StatusFlag.STOPPED);
		
		/* Stack walked forward as if 3 bytes written - but nothing is written!
		 * This is because, internally, the NES tries to push PC and P (24 bits total) as
		 * with NMI calls or BRKs. The problem is, writes are disabled while the reset
		 * pin is held high! So the register decrements but nothing is written.
		 */
		registers.offsetStackPointer(-3);
	}

	public void hardReset() {
		System.out.println("HardReset");
		
		registers.set(StatusFlag.INTERRUPT_DISABLE);
		registers.clear(StatusFlag.STOPPED);
		
		registers.setA(0);
		registers.setX(0);
		registers.setY(0);
		registers.setS(0xFD);
		
		registers.setPC(-1);
	}
	
	
	public boolean isHung() {
		return registers.isSet(StatusFlag.STOPPED);
	}
	
	public void triggerBRK(Bus bus, Cpu cpu) {
		long returnAddress = registers.getPC();
		
		pushAddress(bus, registers, returnAddress);
		
		registers.set(StatusFlag.B);
		registers.set(StatusFlag.INTERRUPT_DISABLE);
		int statusValue = registers.getP();
		push(bus, registers, statusValue);
		
		long interruptVector = bus.read(0xFFFE) & 0xFF;
		interruptVector |= (bus.read(0xFFFF)&0xFF) << 8;
		
		registers.setPC(interruptVector);
	}
	
	public void triggerNMI(Bus bus) {
		
		int statusValue = registers.getP();
		long returnAddress = registers.getPC();
		
		pushAddress(bus, registers, returnAddress);
		push(bus, registers, statusValue);
		
		long interruptVector = bus.read(0xFFFA) & 0xFF;
		interruptVector |= (bus.read(0xFFFB)&0xFF) << 8;
		
		
		System.out.println("NMI Fired: Jumping to code at "+Integer.toHexString((int) interruptVector));
		
		registers.setPC(interruptVector);
	}
	
	public RegisterFile getRegisters() {
		return registers;
	}
	
	/**
	 * Unpack the instruction-stream ring buffer and return an in-order List
	 * @return a List of the last N instructions (currently 256)
	 */
	public List<String> getInstructionStream() {
		return new ArrayList<String>(instructionStream);
	}
	
	public static class Breakpoint {
		public long addr;
	}
	
	public ConsumerEvent<String> onInstruction() {
		return onInstruction;
	}
	
	public static String hexByte(int i) {
		String result = Integer.toHexString(i & 0xFF).toUpperCase();
		if (result.length()>=2) {
			return result;
		} else {
			return "0"+result;
		}
	}
	
	public static String hexShort(long i) {
		String result = Integer.toHexString((int) (i & 0xFFFFL)).toUpperCase();
		while (result.length()<4) result = "0"+result;
		return result;
	}
}
