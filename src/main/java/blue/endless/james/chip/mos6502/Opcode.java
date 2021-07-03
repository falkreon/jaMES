package blue.endless.james.chip.mos6502;

import java.util.List;

import blue.endless.james.core.felines.NesCore;
import blue.endless.james.host.Bus;

public class Opcode {
	final String name;
	final MemoryMode mode;
	//int cycles;
	final Microcode logic;
	//final boolean extraCycle;
	
	public Opcode(String name, MemoryMode mode, Microcode logic) {
		this.logic = logic;
		this.name = name;
		this.mode = mode;
	}
	
	public static final Microcode NOP = (bus, regs) -> 1;
	
	public static final Microcode BRK = (bus, regs) -> {
		long returnAddress = regs.getPC();
		
		Cpu.pushAddress(bus, regs, returnAddress);
		
		regs.set(StatusFlag.B);
		regs.set(StatusFlag.INTERRUPT_DISABLE);
		int statusValue = regs.getP();
		Cpu.push(bus, regs, statusValue);
		
		long interruptVector = bus.read(0xFFFE) & 0xFF;
		interruptVector |= (bus.read(0xFFFF)&0xFF) << 8;
		
		regs.setPC(interruptVector);
		
		//bus.cpu.triggerBRK(bus);
		return 6;
	};
	
	public static final int sec(NesCore bus, RegisterFile regs) {
		regs.set(StatusFlag.CARRY);
		
		return 1;
	}
	
	public static final Microcode KIL = (bus, regs) -> {
		regs.hang();
		System.out.println("KIL instruction stopped CPU execution at "+Integer.toHexString((int) regs.getInstructionAddress()));
		//List<String> stream = bus.cpu.getInstructionStream();
		//System.out.println("Instruction stream leading up to the Incident ("+stream.size()+" entries)");
		//for(String s : stream) {
		//	System.out.println("  "+s);
		//}
		System.out.println("End Stream.");
		System.out.println("Stack pointer: "+Integer.toHexString(regs.getS())+" Probable Stack Top: "+Integer.toHexString((int) regs.getProbableStackTop()));
		//bus.printStackTrace();
		return 3; // NOT WELL DOCUMENTED. Using mnemonic from the MOS 6502 instead of the 65C02, but the cycle count for the 65C02 because no count is attested for the base MOS.
	};
	
	/* *----------------------* *
	 * | LOAD / STORE & STACK | *
	 * *----------------------* */
	
	public static final Microcode LDA = (bus, regs) -> {
		regs.setA(bus.read(regs.getFetchLocation()));
		return 1;
	};
	
	public static final Microcode LDY = (bus, regs) -> {
		regs.setY(bus.read(regs.getFetchLocation()));
		return 1;
	};
	
	public static final Microcode LDX = (bus, regs) -> {
		regs.setX(bus.read(regs.getFetchLocation()));
		return 1;
	};
	
	public static final Microcode STA = (bus, regs) -> {
		bus.write(regs.getFetchLocation(), regs.getA());
		return 1;
	};
	
	public static final Microcode STY = (bus, regs) -> {
		bus.write(regs.getFetchLocation(), regs.getY());
		
		return 1;
	};
	
	public static final Microcode STX = (bus, regs) -> {
		bus.write(regs.getFetchLocation(), regs.getX());
		
		return 1;
	};
	
	public static final Microcode PHA = (bus, regs) -> {
		Cpu.push(bus, regs, regs.getA());
		return 2;
	};
	
	public static final Microcode PLA = (bus, regs) -> {
		regs.setA(Cpu.pop(bus, regs));
		return 3;
	};
	
	public static final Microcode PHP = (bus, regs) -> {
		Cpu.push(bus, regs, StatusFlag.B.set(regs.getP()) );
		return 2;
	};
	
	public static final Microcode PLP = (bus, regs) -> {
		regs.setP(StatusFlag.B.clear(Cpu.pop(bus, regs))); //TODO: Clear more bits like BCD
		return 3;
	};
	
	public static final Microcode TAX = (bus, regs) -> {
		regs.setX(regs.getA());
		return 1;
	};
	
	public static final Microcode TAY = (bus, regs) -> {
		regs.setY(regs.getA());
		return 1;
	};
	
	public static final Microcode TSX = (bus, regs) -> {
		regs.setX(regs.getS());
		return 1;
	};
	
	public static final Microcode TXA = (bus, regs) -> {
		regs.setA(regs.getX());
		return 1;
	};
	
	public static final Microcode TXS = (bus, regs) -> {
		regs.setS(regs.getX());
		regs.indicateProbableStackTop();
		System.out.println("Stack top might have been set to "+Integer.toHexString(regs.getS() & 0xFF));
		return 1;
	};
	
	public static final Microcode TYA = (bus, regs) -> {
		regs.setA(regs.getY());
		return 1;
	};
	
	/* *-------------* *
	 * | STATUS BITS | *
	 * *-------------* */
	
	public static final Microcode CLC = (bus, regs) -> {
		regs.clear(StatusFlag.CARRY);
		return 1;
	};
	
	public static final Microcode SEC = (bus, regs) -> {
		regs.set(StatusFlag.CARRY);
		return 1;
	};
	
	public static final Microcode CLI = (bus, regs) -> {
		regs.clear(StatusFlag.INTERRUPT_DISABLE);
		return 1;
	};
	
	public static final Microcode SEI = (bus, regs) -> {
		regs.set(StatusFlag.INTERRUPT_DISABLE);
		return 1;
	};
	
	public static final Microcode CLV = (bus, regs) -> {
		regs.clear(StatusFlag.OVERFLOW);
		return 1;
	};
	
	//Doesn't affect anything; no BCD support
	public static final Microcode CLD = (bus, regs) -> {
		regs.clear(StatusFlag.DECIMAL);
		return 1;
	};
	
	//Doesn't affect anything; no BCD support
	public static final Microcode SED = (bus, regs) -> {
		regs.set(StatusFlag.DECIMAL);
		return 1;
	};
	
	
	
	/* *--------------------* *
	 * | BITWISE OPERATIONS | *
	 * *--------------------* */
	
	public static final Microcode BIT = (bus, regs) -> {
		int work = bus.read(regs.getFetchLocation()) & 0xFF;
		int val = work & regs.getA();
		regs.set(StatusFlag.ZERO, val==0);
		regs.set(StatusFlag.OVERFLOW, (val & 0x40) != 0);
		regs.set(StatusFlag.CARRY, (val & 0x80) != 0);
		
		return 1;
	};
	
	public static final Microcode AND = (bus, regs) -> {
		regs.setA(regs.getA() & bus.read(regs.getFetchLocation()));
		return 1;
	};
	
	public static final Microcode ORA = (bus, regs) -> {
		regs.setA(regs.getA() | bus.read(regs.getFetchLocation()));
		return 1;
	};
	
	public static final Microcode EOR = (bus, regs) -> {
		regs.setA(regs.getA() ^ bus.read(regs.getFetchLocation()));
		return 1;
	};
	
	/**
	 * The A register is shifted left by one bit, and the "extra" top bit moved to the carry flag. The low bit is set to zero.
	 */
	public static final Microcode ASL_A = (bus, regs) -> {
		int work = regs.getA();
		regs.set(StatusFlag.CARRY, (work & 0x80)!=0); //Move the top bit into carry
		work = (work << 1) & 0xFF; //Perform the shift
		regs.setA(work); //Will trip ZN if appropriate
		
		return 1; //ASL_A is one cycle instr fetch, zero memory access, and one cycle for the modify-write in this method
	};
	
	/**
	 * The target is shifted left by one bit, and the "extra" top bit moved to the carry flag. The low bit is set to zero.
	 * The value is then stored back at the original location.
	 */
	public static final Microcode ASL = (bus, regs) -> {
		int work = bus.read(regs.getFetchLocation()) & 0xFF;
		regs.set(StatusFlag.CARRY, (work & 0x80)!=0); //Move the top bit into carry
		work = regs.affectZN((work << 1) & 0xFF); //perform the shift, and trip ZN if appropriate
		bus.write(regs.getFetchLocation(), work); //store back at the original location
		
		//ASL_ZP is base instruction decode of 1, plus 1 zeropage address fetch, plus the 3-cycle read-modify-write from the logic in this method.
		return 3;
	};
	
	public static final Microcode LSR_A = (bus, regs) -> {
		int work = regs.getA();
		regs.set(StatusFlag.CARRY, (work & 1) != 0);
		work = regs.affectZN((work >>> 1) & 0x7F);
		regs.setA(work & 0xFF);
		
		return 1;
	};
	
	public static final Microcode LSR = (bus, regs) -> {
		int work = bus.read(regs.getFetchLocation()) & 0xFF;
		regs.set(StatusFlag.CARRY, (work & 1) != 0);
		work = regs.affectZN((work >>> 1) & 0x7F);
		bus.write(regs.getFetchLocation(), work);
		
		return 3;
	};
	
	/**
	 * The A register is shifted right by one bit. The carry flag is shifted into the high bit, and the low bit that is "lost" is shifted into the carry flag.
	 */
	public static final Microcode ROR_A = (bus, regs) -> {
		int work = regs.getA();
		boolean oldCarry = regs.isSet(StatusFlag.CARRY); //save out carry bit
		
		regs.set(StatusFlag.CARRY, (work & 1) != 0); //stash the low bit in Carry
		work = (work >> 1) & 0xFF; //perform the shift
		if (oldCarry) work |= 0x80; //shift the carry back into the top bit
		regs.setA(work); //trips ZN if needed
		
		return 1;
	};
	
	/** The target is shifted right by one bit. The carry flag is shifted into the high bit, and the low bit that is "lost" is shifted into the carry flag.
	 * The value is then stored back at the original location.
	 */
	public static final Microcode ROR = (bus, regs) -> {
		int work = bus.read(regs.getFetchLocation()) & 0xFF;
		boolean oldCarry = regs.isSet(StatusFlag.CARRY); //save out carry bit
		
		regs.set(StatusFlag.CARRY, (work & 1) != 0); //stash the low bit in Carry
		work = (work >> 1) & 0xFF; //perform the shift
		if (oldCarry) work |= 0x80; //shift the carry back into the top bit
		regs.affectZN(work); //trip flags if needed
		bus.write(regs.getFetchLocation(), work);
		
		return 3;
	};
	
	public static final Microcode ROL_A = (bus, regs) -> {
		int work = regs.getA();
		boolean oldCarry = regs.isSet(StatusFlag.CARRY); //save out carry bit
		
		regs.set(StatusFlag.CARRY, (work & 0x80) != 0); //stash the high bit into Carry
		work = (work << 1) & 0xFF; //perform the shift
		if (oldCarry) work |= 1; //shift the carry back into the bottom bit
		regs.setA(work);
		
		return 1;
	};
	
	public static final Microcode ROL = (bus, regs) -> {
		int work = bus.read(regs.getFetchLocation()) & 0xFF;
		boolean oldCarry = regs.isSet(StatusFlag.CARRY); //save out carry bit
		
		regs.set(StatusFlag.CARRY, (work & 0x80) != 0); //stash the low bit in Carry
		work = (work << 1) & 0xFF; //perform the shift
		if (oldCarry) work |= 1; //shift the carry back into the bottom bit
		regs.affectZN(work); //trip flags if needed
		bus.write(regs.getFetchLocation(), work);
		
		return 3;
	};
	
	/* *---------------* *
	 * |   ALU OPS     | *
	 * *---------------* */
	
	
	public static final Microcode INC = (bus, regs) -> {
		int work = bus.read(regs.getFetchLocation()) & 0xFF;
		work = regs.affectZN(work+1); //Note that C is never set!
		bus.write(regs.getFetchLocation(), work);
		
		return 3; //INC_ZP == fetch 1 + zp access 1 + this logic's rmw 3 == 5 cycles
	};
	
	public static final Microcode DEC = (bus, regs) -> {
		int val = bus.read(regs.getFetchLocation());
		val = (val - 1) & 0xFF;
		regs.affectZN(val);
		bus.write(regs.getFetchLocation(), val);
		
		return 3;
	};
	
	public static final Microcode INX = (bus, regs) -> {
		regs.setX(regs.getX() + 1);
		return 1;
	};
	
	public static final Microcode DEX = (bus, regs) -> {
		regs.setX(regs.getX() - 1);
		return 1;
	};
	
	public static final Microcode INY = (bus, regs) -> {
		regs.setY(regs.getY() + 1);
		return 1;
	};
	
	public static final Microcode DEY = (bus, regs) -> {
		regs.setY(regs.getY() - 1);
		return 1;
	};
	
	public static final Microcode ADC = (bus, regs) -> {
		int val = bus.read(regs.getFetchLocation()) & 0xFF;
		int carry = (regs.isSet(StatusFlag.CARRY)) ? 1 : 0;
		int result = val + carry + (regs.getA() & 0xFF);
		
		
		regs.set(StatusFlag.CARRY, (result & 0xFF00) != 0);
		result &= 0xFF;
		
		//Figure out overflow
		boolean valPositive = (val&0x80) != 0;
		boolean aPositive = (regs.getA()&0x80) != 0;
		boolean resultPositive = (result&0x80) != 0;
		if ((valPositive && aPositive && !resultPositive) || (!valPositive && !aPositive && resultPositive)) {
			regs.set(StatusFlag.OVERFLOW);
		} else if ((valPositive && !aPositive) || (!valPositive && aPositive)) {
			regs.clear(StatusFlag.OVERFLOW);
		} else {
			regs.clear(StatusFlag.OVERFLOW);
		}
		
		regs.setA(result);
		
		return 1;
	};
	
	public static final Microcode SBC = (bus, regs) -> {
		int val = bus.read(regs.getFetchLocation()) & 0xFF;
		val = val ^ 0xFF; //inverts just the bottom 8 bits - we're skipping the +1 because we get it for free from the carry-in
		
		int carry = (regs.isSet(StatusFlag.CARRY)) ? 1 : 0;
		int result = val + carry + (regs.getA() & 0xFF);
		
		regs.set(StatusFlag.CARRY, (result & 0xFF00) != 0);
		result &= 0xFF;
		
		//Figure out overflow
		boolean valPositive = (val&0x80) != 0;
		boolean aPositive = (regs.getA()&0x80) != 0;
		boolean resultPositive = (result&0x80) != 0;
		if ((valPositive && aPositive && !resultPositive) || (!valPositive && !aPositive && resultPositive)) {
			regs.set(StatusFlag.OVERFLOW);
		} else if ((valPositive && !aPositive) || (!valPositive && aPositive)) {
			regs.clear(StatusFlag.OVERFLOW);
		} else {
			regs.clear(StatusFlag.OVERFLOW);
		}
		
		regs.setA(result);
		
		
		/*
		int carry = (regs.isSet(StatusFlag.CARRY)) ? 0 : 1; //Flip the value
		int result = ( regs.getA() - val - carry );
		if ((result & 0xFFFFFF00) != 0) {
			regs.set(StatusFlag.CARRY);
		}
		result = result & 0xFF;
		regs.setA(result); //Affects ZN
		*/
		return 1;
	};
	
	public static final Microcode CMP = (bus, regs) -> {
		int memoryValue = bus.read(regs.getFetchLocation()) & 0xFF;
		int accValue = regs.getA() & 0xFF;
		regs.affectCZN(accValue - memoryValue);
		
		return 1;
	};
	
	public static final Microcode CPX = (bus, regs) -> {
		int memoryValue = bus.read(regs.getFetchLocation()) & 0xFF;
		int accValue = regs.getX() & 0xFF;
		regs.affectCZN(accValue - memoryValue);
		
		return 1;
	};
	
	public static final Microcode CPY = (bus, regs) -> {
		int memoryValue = bus.read(regs.getFetchLocation()) & 0xFF;
		int accValue = regs.getY() & 0xFF;
		regs.affectCZN(accValue - memoryValue);
		
		return 1;
	};
	
	/* *------------------* *
	 * | BRANCHES & JUMPS | *
	 * *------------------* */
	
	public static final Microcode BPL = (bus, regs) -> {
		if (!regs.isSet(StatusFlag.NEGATIVE)) {
			long branchTarget = regs.getFetchLocation() & 0xFFFF;
			long pc = regs.getPC() & 0xFFFF;
			
			regs.setPC(branchTarget);
			
			int extraCycle = ((branchTarget>>8)==(pc>>8)) ? 0 : 1;
			
			return 2 + extraCycle; //3-4 cycles, depending on whether we branch across a page boundary
			
		} else {
			return 1; //2 cycles total
		}
	};
	
	public static final Microcode BMI = (bus, regs) -> {
		if (regs.isSet(StatusFlag.NEGATIVE)) {
			long branchTarget = regs.getFetchLocation() & 0xFFFF;
			long pc = regs.getPC() & 0xFFFF;
			
			regs.setPC(branchTarget);
			
			int extraCycle = ((branchTarget>>8)==(pc>>8)) ? 0 : 1;
			
			return 2 + extraCycle; //3-4 cycles, depending on whether we branch across a page boundary
			
		} else {
			return 1; //2 cycles total
		}
	};
	
	public static final Microcode BVS = (bus, regs) -> {
		if (regs.isSet(StatusFlag.OVERFLOW)) {
			long branchTarget = regs.getFetchLocation() & 0xFFFF;
			long pc = regs.getPC() & 0xFFFF;
			
			regs.setPC(branchTarget);
			
			int extraCycle = ((branchTarget>>8)==(pc>>8)) ? 0 : 1;
			
			return 2 + extraCycle; //3-4 cycles, depending on whether we branch across a page boundary
			
		} else {
			return 1; //2 cycles total
		}
	};
	
	public static final Microcode BVC = (bus, regs) -> {
		if (!regs.isSet(StatusFlag.OVERFLOW)) {
			long branchTarget = regs.getFetchLocation() & 0xFFFF;
			long pc = regs.getPC() & 0xFFFF;
			
			regs.setPC(branchTarget);
			
			int extraCycle = ((branchTarget>>8)==(pc>>8)) ? 0 : 1;
			
			return 2 + extraCycle; //3-4 cycles, depending on whether we branch across a page boundary
			
		} else {
			return 1; //2 cycles total
		}
	};
	
	public static final Microcode BCC = (bus, regs) -> {
		if (!regs.isSet(StatusFlag.CARRY)) {
			long branchTarget = regs.getFetchLocation() & 0xFFFF;
			long pc = regs.getPC() & 0xFFFF;
			
			regs.setPC(branchTarget);
			
			int extraCycle = ((branchTarget>>8)==(pc>>8)) ? 0 : 1;
			
			return 2 + extraCycle; //3-4 cycles, depending on whether we branch across a page boundary
			
		} else {
			return 1; //2 cycles total
		}
	};
	
	public static final Microcode BCS = (bus, regs) -> {
		if (regs.isSet(StatusFlag.CARRY)) {
			long branchTarget = regs.getFetchLocation() & 0xFFFF;
			long pc = regs.getPC() & 0xFFFF;
			
			regs.setPC(branchTarget);
			
			int extraCycle = ((branchTarget>>8)==(pc>>8)) ? 0 : 1;
			
			return 2 + extraCycle; //3-4 cycles, depending on whether we branch across a page boundary
			
		} else {
			return 1; //2 cycles total
		}
	};
	
	public static final Microcode BNE = (bus, regs) -> {
		if (!regs.isSet(StatusFlag.ZERO)) {
			long branchTarget = regs.getFetchLocation() & 0xFFFF;
			long pc = regs.getPC() & 0xFFFF;
			
			regs.setPC(branchTarget);
			
			int extraCycle = ((branchTarget>>8)==(pc>>8)) ? 0 : 1;
			
			return 2 + extraCycle; //3-4 cycles, depending on whether we branch across a page boundary
			
		} else {
			return 1; //2 cycles total
		}
	};
	
	public static final Microcode BEQ = (bus, regs) -> {
		if (regs.isSet(StatusFlag.ZERO)) {
			long branchTarget = regs.getFetchLocation() & 0xFFFF;
			long pc = regs.getPC() & 0xFFFF;
			
			regs.setPC(branchTarget);
			
			int extraCycle = ((branchTarget>>8)==(pc>>8)) ? 0 : 1;
			
			return 2 + extraCycle; //3-4 cycles, depending on whether we branch across a page boundary
			
		} else {
			return 1; //2 cycles total
		}
	};
	
	public static final Microcode JMP = (bus, regs) -> {
		regs.setPC(regs.getFetchLocation());
		return 1;
	};
	
	public static final Microcode JSR = (bus, regs) -> {
		Cpu.pushAddress(bus, regs, regs.getPC());
		
		//bus.cpu.push(bus, regs, (int)regs.getPC() & 0xFF);
		//bus.cpu.push(bus, regs, (int)((regs.getPC()) >>> 8) & 0xFF);
		
		//regs.pushCallStack("JSR $"+Integer.toHexString((int) regs.getFetchLocation()), regs.getInstructionAddress(), regs.getFetchLocation());
		
		regs.setPC(regs.getFetchLocation());
		
		return 1; 
	};
	
	public static final Microcode RTS = (bus, regs) -> {
		long addr = Cpu.popAddress(bus, regs);
		//int addr = bus.cpu.pop(bus, regs) << 8;
		//addr |= bus.cpu.pop(bus, regs);
		//regs.pushCallStack("RET", regs.getInstructionAddress(), addr);
		
		regs.setPC(addr & 0xFFFF);
		
		return 1;
	};
	
	public static final Microcode RTI = (bus, regs) -> {
		//System.out.println("RTI");
		int p = Cpu.pop(bus, regs);
		regs.setP(StatusFlag.B.clear(p));
		
		long addr = Cpu.popAddress(bus, regs);
		//long addr = bus.cpu.pop(bus, regs) << 8;
		//addr |= bus.cpu.pop(bus, regs);
		
		regs.setPC(addr);
		
		return 5;
	};
	
	/* *-----------------* *
	 * |     ILLEGAL     | *
	 * *-----------------* */
	
	/**
	 * Shifts the memory location left by 1, and then ORs it with A. Modeled as ASL+ORA.
	 */
	public static final Microcode SLO = (bus, regs) -> {
		return Math.max(ASL.execute(bus, regs), ORA.execute(bus, regs));
	};
	
	/**
	 * Rotates the memory location left one bit, and then ANDs it with A. Modeled as ROL+AND.
	 */
	public static final Microcode RLA = (bus, regs) -> {
		return Math.max(ROL.execute(bus, regs), AND.execute(bus, regs));
	};
	
	
	@FunctionalInterface
	public static interface Microcode {
		int execute(Bus bus, RegisterFile regs);
	}
	
	public static interface MemoryModeTrace {
		String trace(Bus bus, RegisterFile regs);
	}
	
	private static MemoryMode IMPL= MemoryMode.IMPLIED;
	private static MemoryMode IMM = MemoryMode.IMMEDIATE;
	private static MemoryMode ZP  = MemoryMode.ZEROPAGE;
	private static MemoryMode ZPX = MemoryMode.ZEROPAGE_X;
	private static MemoryMode ZPY = MemoryMode.ZEROPAGE_Y;
	private static MemoryMode ABS = MemoryMode.ABSOLUTE;
	private static MemoryMode ABSX= MemoryMode.ABSOLUTE_X;
	private static MemoryMode ABSY= MemoryMode.ABSOLUTE_Y;
	private static MemoryMode IND = MemoryMode.INDIRECT;
	private static MemoryMode IDX = MemoryMode.INDIRECT_X;
	private static MemoryMode IDY = MemoryMode.INDIRECT_Y;
	private static MemoryMode REL = MemoryMode.RELATIVE;
	
	private static MemoryModeTrace T_NO = (bus, regs) -> "";
	
	private static MemoryModeTrace T_A = (bus, regs) -> {
		return " A";
	};
	
	private static MemoryModeTrace T_IMM = (bus, regs) -> {
		int val = bus.read(regs.getInstructionAddress()+1) & 0xFF;
		return " #$"+Cpu.hexByte(val);
	};
	
	private static MemoryModeTrace T_ZP = (bus, regs) -> {
		int val = bus.read(regs.getInstructionAddress()+1) & 0xFF;
		return " $"+Cpu.hexByte(val);
	};
	
	private static MemoryModeTrace T_ZPX = (bus, regs) -> {
		int val = bus.read(regs.getInstructionAddress()+1) & 0xFF;
		return " $"+Cpu.hexByte(val)+" + $#"+Cpu.hexByte(regs.getX())+" = $"+Cpu.hexByte(val+regs.getX());
	};
	
	private static MemoryModeTrace T_ZPY = (bus, regs) -> {
		int val = bus.read(regs.getInstructionAddress()+1) & 0xFF;
		return " $"+Cpu.hexByte(val)+" + $#"+Cpu.hexByte(regs.getY())+" = $"+Cpu.hexByte(val+regs.getX());
	};
	
	private static MemoryModeTrace T_ABS = (bus, regs) -> {
		int valLo = bus.read(regs.getInstructionAddress()+1) & 0xFF;
		int valHi = bus.read(regs.getInstructionAddress()+2) & 0xFF;
		int val = (valHi << 8) | valLo;
		return " $"+Cpu.hexShort(val);
	};
	
	private static MemoryModeTrace T_ABA = (bus, regs) -> {
		int valLo = bus.read(regs.getInstructionAddress()+1) & 0xFF;
		int valHi = bus.read(regs.getInstructionAddress()+2) & 0xFF;
		int val = (valHi << 8) | valLo;
		return " $"+Cpu.hexShort(val)+" = #$"+Cpu.hexByte(regs.getA());
	};
	
	private static MemoryModeTrace T_ABX = (bus, regs) -> {
		int valLo = bus.read(regs.getInstructionAddress()+1) & 0xFF;
		int valHi = bus.read(regs.getInstructionAddress()+2) & 0xFF;
		int val = (valHi << 8) | valLo;
		return " $"+Cpu.hexShort(val)+" + $#"+Cpu.hexByte(regs.getX())+" = $"+Cpu.hexShort(val+regs.getX());
	};
	
	private static MemoryModeTrace T_ABY = (bus, regs) -> {
		int valLo = bus.read(regs.getInstructionAddress()+1) & 0xFF;
		int valHi = bus.read(regs.getInstructionAddress()+2) & 0xFF;
		int val = (valHi << 8) | valLo;
		return " $"+Cpu.hexShort(val)+" + $#"+Cpu.hexByte(regs.getY())+" = $"+Cpu.hexShort(val+regs.getX());
	};
	
	private static MemoryModeTrace T_IND = (bus, regs) -> {
		return "";
	};
	
	private static MemoryModeTrace T_IDX = (bus, regs) -> {
		return "";
	};
	
	private static MemoryModeTrace T_IDY = (bus, regs) -> {
		return "";
	};
	
	private static MemoryModeTrace T_REL = (bus, regs) -> {
		return "";
	};
	
	// 6502/ 2A03 information, in table form
	public static final String[] INSTRUCTION_NAME = {
	// * = these columns are "gray columns" in the wiki, populated solely by illegal opcodes
	/*        00     01     02     03*    04     05     06     07*    08     09     0A     0B*    0C     0D     0E     0F*    10     11     12     13*    14     15     16     17*    18     19     1A     1B*    1C     1D     1E     1F*    */
	/* 00 */ "BRK", "ORA", "KIL", "SLO", "NOP", "ORA", "ASL", "SLO", "PHP", "ORA", "ASL", "ANC", "NOP", "ORA", "ASL", "SLO", "BPL", "ORA", "KIL", "SLO", "NOP", "ORA", "ASL", "SLO", "CLC", "ORA", "NOP", "SLO", "NOP", "ORA", "ASL", "SLO",
	/* 20 */ "JSR", "AND", "KIL", "RLA", "BIT", "AND", "ROL", "RLA", "PLP", "AND", "ROL", "ANC", "BIT", "AND", "ROL", "RLA", "BMI", "AND", "KIL", "RLA", "NOP", "AND", "ROL", "RLA", "SEC", "AND", "NOP", "RLA", "NOP", "AND", "ROL", "RLA",
	/* 40 */ "RTI", "EOR", "KIL", "SRE", "NOP", "EOR", "LSR", "SRE", "PHA", "EOR", "LSR", "ALR", "JMP", "EOR", "LSR", "SRE", "BVC", "EOR", "KIL", "SRE", "NOP", "EOR", "LSR", "SRE", "CLI", "EOR", "NOP", "SRE", "NOP", "EOR", "LSR", "SRE",
	/* 60 */ "RTS", "ADC", "KIL", "RRA", "NOP", "ADC", "ROR", "RRA", "PLA", "ADC", "ROR", "ARR", "JMP", "ADC", "ROR", "RRA", "BVS", "ADC", "KIL", "RRA", "NOP", "ADC", "ROR", "RRA", "SEI", "ADC", "NOP", "RRA", "NOP", "ADC", "ROR", "RRA",
	/* 80 */ "NOP", "STA", "NOP", "SAX", "STY", "STA", "STX", "SAX", "DEY", "NOP", "TXA", "XAA", "STY", "STA", "STX", "SAX", "BCC", "STA", "KIL", "AHX", "STY", "STA", "STX", "SAX", "TYA", "STA", "TXS", "TAS", "SHY", "STA", "SHX", "AHX",
	/* A0 */ "LDY", "LDA", "LDX", "LAX", "LDY", "LDA", "LDX", "LAX", "TAY", "LDA", "TAX", "LAX", "LDY", "LDA", "LDX", "LAX", "BCS", "LDA", "KIL", "LAX", "LDY", "LDA", "LDX", "LAX", "CLV", "LDA", "TSX", "LAS", "LDY", "LDA", "LDX", "LAX",
	/* C0 */ "CPY", "CMP", "NOP", "DCP", "CPY", "CMP", "DEC", "DCP", "INY", "CMP", "DEX", "AXS", "CPY", "CMP", "DEC", "DCP", "BNE", "CMP", "KIL", "DCP", "NOP", "CMP", "DEC", "DCP", "CLD", "CMP", "NOP", "DCP", "NOP", "CMP", "DEC", "DCP",
	/* E0 */ "CPX", "SBC", "NOP", "ISC", "CPX", "SBC", "INC", "ISC", "INX", "SBC", "NOP", "SBC", "CPX", "SBC", "INC", "ISC", "BEQ", "SBC", "KIL", "ISC", "NOP", "SBC", "INC", "ISC", "SED", "SBC", "NOP", "ISC", "NOP", "SBC", "INC", "ISC",
	};
	
	public static final int[] INSTRUCTION_SIZE = {
	//       ABS
	//       IMPL   IDX    IMPL   IDX     ZP     ZP     ZP     ZP    IMPL   IMM    IMPL   IMM    ABS    ABS    ABS    ABS    REL    IDY    IMPL   IDY    ZPX    ZPX    ZPX    ZPX    IMPL   ABSY   IMPL   ABSY   ABSX   ABSX   ABSX    ABSX
	//       IMM           IMM                                                                   IND                                                                   ZPY    ZPY
	/*        00     01     02     03*    04     05     06     07*    08     09     0A     0B*    0C     0D     0E     0F*    10     11     12     13*    14     15     16     17*    18     19     1A     1B*    1C     1D     1E     1F*    */
	/* 00 */  1,     2,     1,     2,     2,     2,     2,     2,     1,     2,     1,     2,     3,     3,     3,     3,     2,     2,     1,     2,     2,     2,     2,     2,     1,     3,     1,     3,     3,     3,     3,     3,
	/* 20 */  3,     2,     1,     2,     2,     2,     2,     2,     1,     2,     1,     2,     3,     3,     3,     3,     2,     2,     1,     2,     2,     2,     2,     2,     1,     3,     1,     3,     3,     3,     3,     3,
	/* 40 */  1,     2,     1,     2,     2,     2,     2,     2,     1,     2,     1,     2,     3,     3,     3,     3,     2,     2,     1,     2,     2,     2,     2,     2,     1,     3,     1,     3,     3,     3,     3,     3,
	/* 60 */  1,     2,     1,     2,     2,     2,     2,     2,     1,     2,     1,     2,     3,     3,     3,     3,     2,     2,     1,     2,     2,     2,     2,     2,     1,     3,     1,     3,     3,     3,     3,     3,
	/* 80 */  2,     2,     2,     2,     2,     2,     2,     2,     1,     2,     1,     2,     3,     3,     3,     3,     2,     2,     1,     2,     2,     2,     2,     2,     1,     3,     1,     3,     3,     3,     3,     3,
	/* A0 */  2,     2,     2,     2,     2,     2,     2,     2,     1,     2,     1,     2,     3,     3,     3,     3,     2,     2,     1,     2,     2,     2,     2,     2,     1,     3,     1,     3,     3,     3,     3,     3,
	/* C0 */  2,     2,     2,     2,     2,     2,     2,     2,     1,     2,     1,     2,     3,     3,     3,     3,     2,     2,     1,     2,     2,     2,     2,     2,     1,     3,     1,     3,     3,     3,     3,     3,
	/* E0 */  2,     2,     2,     2,     2,     2,     2,     2,     1,     2,     1,     2,     3,     3,     3,     3,     2,     2,     1,     2,     2,     2,     2,     2,     1,     3,     1,     3,     3,     3,     3,     3,
	};
	
	public static final MemoryMode[] INSTRUCTION_MODE = {
	/*        00     01     02     03*    04     05     06     07*    08     09     0A     0B*    0C     0D     0E     0F*    10     11     12     13*    14     15     16     17*    18     19     1A     1B*    1C     1D     1E     1F*    */
	/* 00 */  IMPL,  IDX,   IMPL,  IDX,   ZP,    ZP,    ZP,    ZP,    IMPL,  IMM,   IMPL,  IMM,   ABS,   ABS,   ABS,   ABS,   REL,   IDY,   IMPL,  IDY,   ZPX,   ZPX,   ZPX,   ZPX,   IMPL,  ABSY,  IMPL,  ABSY,  ABSX,  ABSX,  ABSX,  ABSX,
	/* 20 */  ABS,   IDX,   IMPL,  IDX,   ZP,    ZP,    ZP,    ZP,    IMPL,  IMM,   IMPL,  IMM,   ABS,   ABS,   ABS,   ABS,   REL,   IDY,   IMPL,  IDY,   ZPX,   ZPX,   ZPX,   ZPX,   IMPL,  ABSY,  IMPL,  ABSY,  ABSX,  ABSX,  ABSX,  ABSX,
	/* 40 */  IMPL,  IDX,   IMPL,  IDX,   ZP,    ZP,    ZP,    ZP,    IMPL,  IMM,   IMPL,  IMM,   ABS,   ABS,   ABS,   ABS,   REL,   IDY,   IMPL,  IDY,   ZPX,   ZPX,   ZPX,   ZPX,   IMPL,  ABSY,  IMPL,  ABSY,  ABSX,  ABSX,  ABSX,  ABSX,
	/* 60 */  IMPL,  IDX,   IMPL,  IDX,   ZP,    ZP,    ZP,    ZP,    IMPL,  IMM,   IMPL,  IMM,   IND,   ABS,   ABS,   ABS,   REL,   IDY,   IMPL,  IDY,   ZPX,   ZPX,   ZPX,   ZPX,   IMPL,  ABSY,  IMPL,  ABSY,  ABSX,  ABSX,  ABSX,  ABSX,
	/* 80 */  IMM,   IDX,   IMM,   IDX,   ZP,    ZP,    ZP,    ZP,    IMPL,  IMM,   IMPL,  IMM,   ABS,   ABS,   ABS,   ABS,   REL,   IDY,   IMPL,  IDY,   ZPX,   ZPX,   ZPY,   ZPY,   IMPL,  ABSY,  IMPL,  ABSY,  ABSX,  ABSX,  ABSX,  ABSX,
	/* A0 */  IMM,   IDX,   IMM,   IDX,   ZP,    ZP,    ZP,    ZP,    IMPL,  IMM,   IMPL,  IMM,   ABS,   ABS,   ABS,   ABS,   REL,   IDY,   IMPL,  IDY,   ZPX,   ZPX,   ZPY,   ZPY,   IMPL,  ABSY,  IMPL,  ABSY,  ABSX,  ABSX,  ABSX,  ABSX,
	/* C0 */  IMM,   IDX,   IMM,   IDX,   ZP,    ZP,    ZP,    ZP,    IMPL,  IMM,   IMPL,  IMM,   ABS,   ABS,   ABS,   ABS,   REL,   IDY,   IMPL,  IDY,   ZPX,   ZPX,   ZPX,   ZPX,   IMPL,  ABSY,  IMPL,  ABSY,  ABSX,  ABSX,  ABSX,  ABSX,
	/* E0 */  IMM,   IDX,   IMM,   IDX,   ZP,    ZP,    ZP,    ZP,    IMPL,  IMM,   IMPL,  IMM,   ABS,   ABS,   ABS,   ABS,   REL,   IDY,   IMPL,  IDY,   ZPX,   ZPX,   ZPX,   ZPX,   IMPL,  ABSY,  IMPL,  ABSY,  ABSX,  ABSX,  ABSX,  ABSX,
	};
	
	public static final MemoryModeTrace[] INSTRUCTION_TRACE = {
	/*        00     01     02     03*    04     05     06     07*    08     09     0A     0B*    0C     0D     0E     0F*    10     11     12     13*    14     15     16     17*    18     19     1A     1B*    1C     1D     1E     1F*    */
	/* 00 */  T_NO,  T_IDX, T_NO,  T_IDX, T_ZP,  T_ZP,  T_ZP,  T_ZP,  T_NO,  T_IMM, T_A,   T_IMM, T_ABS, T_ABS, T_ABS, T_ABS, T_REL, T_IDY, T_NO,  T_IDY, T_ZPX, T_ZPX, T_ZPX, T_ZPX, T_NO,  T_ABY, T_NO,  T_ABY, T_ABX, T_ABX, T_ABX, T_ABX,
	/* 20 */  T_ABS, T_IDX, T_NO,  T_IDX, T_ZP,  T_ZP,  T_ZP,  T_ZP,  T_NO,  T_IMM, T_A,   T_IMM, T_ABS, T_ABS, T_ABS, T_ABS, T_REL, T_IDY, T_NO,  T_IDY, T_ZPX, T_ZPX, T_ZPX, T_ZPX, T_NO,  T_ABY, T_NO,  T_ABY, T_ABX, T_ABX, T_ABX, T_ABX,
	/* 40 */  T_NO,  T_IDX, T_NO,  T_IDX, T_ZP,  T_ZP,  T_ZP,  T_ZP,  T_NO,  T_IMM, T_A,   T_IMM, T_ABS, T_ABS, T_ABS, T_ABS, T_REL, T_IDY, T_NO,  T_IDY, T_ZPX, T_ZPX, T_ZPX, T_ZPX, T_NO,  T_ABY, T_NO,  T_ABY, T_ABX, T_ABX, T_ABX, T_ABX,
	/* 60 */  T_NO,  T_IDX, T_NO,  T_IDX, T_ZP,  T_ZP,  T_ZP,  T_ZP,  T_NO,  T_IMM, T_A,   T_IMM, T_IND, T_ABS, T_ABS, T_ABS, T_REL, T_IDY, T_NO,  T_IDY, T_ZPX, T_ZPX, T_ZPX, T_ZPX, T_NO,  T_ABY, T_NO,  T_ABY, T_ABX, T_ABX, T_ABX, T_ABX,
	/* 80 */  T_IMM, T_IDX, T_IMM, T_IDX, T_ZP,  T_ZP,  T_ZP,  T_ZP,  T_NO,  T_IMM, T_NO,  T_IMM, T_ABS, T_ABA, T_ABS, T_ABS, T_REL, T_IDY, T_NO,  T_IDY, T_ZPX, T_ZPX, T_ZPY, T_ZPY, T_NO,  T_ABY, T_NO,  T_ABY, T_ABX, T_ABX, T_ABX, T_ABX,
	/* A0 */  T_IMM, T_IDX, T_IMM, T_IDX, T_ZP,  T_ZP,  T_ZP,  T_ZP,  T_NO,  T_IMM, T_NO,  T_IMM, T_ABS, T_ABA, T_ABS, T_ABS, T_REL, T_IDY, T_NO,  T_IDY, T_ZPX, T_ZPX, T_ZPY, T_ZPY, T_NO,  T_ABY, T_NO,  T_ABY, T_ABX, T_ABX, T_ABX, T_ABX,
	/* C0 */  T_IMM, T_IDX, T_IMM, T_IDX, T_ZP,  T_ZP,  T_ZP,  T_ZP,  T_NO,  T_IMM, T_NO,  T_IMM, T_ABS, T_ABS, T_ABS, T_ABS, T_REL, T_IDY, T_NO,  T_IDY, T_ZPX, T_ZPX, T_ZPX, T_ZPX, T_NO,  T_ABY, T_NO,  T_ABY, T_ABX, T_ABX, T_ABX, T_ABX,
	/* E0 */  T_IMM, T_IDX, T_IMM, T_IDX, T_ZP,  T_ZP,  T_ZP,  T_ZP,  T_NO,  T_IMM, T_NO,  T_IMM, T_ABS, T_ABS, T_ABS, T_ABS, T_REL, T_IDY, T_NO,  T_IDY, T_ZPX, T_ZPX, T_ZPX, T_ZPX, T_NO,  T_ABY, T_NO,  T_ABY, T_ABX, T_ABX, T_ABX, T_ABX,
	};
	
	public static final Microcode[] INSTRUCTION_LOGIC = {
	/*        00     01     02     03*    04     05     06     07*    08     09     0A     0B*    0C     0D     0E     0F*    10     11     12     13*    14     15     16     17*    18     19     1A     1B*    1C     1D     1E     1F*    */
	/* 00 */  BRK,   ORA,   KIL,   SLO,   NOP,   ORA,   ASL,   SLO,   PHP,   ORA,   ASL_A, null,  NOP,   ORA,   ASL,   SLO,   BPL,   ORA,   KIL,   SLO,   NOP,   ORA,   ASL,   SLO,   CLC,   ORA,   NOP,   SLO,   NOP,   ORA,   ASL,   SLO,
	/* 20 */  JSR,   AND,   KIL,   RLA,   BIT,   AND,   ROL,   RLA,   PLP,   AND,   ROL_A, null,  BIT,   AND,   ROL,   RLA,   BMI,   AND,   KIL,   RLA,   NOP,   AND,   ROL,   RLA,   SEC,   AND,   NOP,   RLA,   NOP,   AND,   ROL,   RLA,
	/* 40 */  RTI,   EOR,   KIL,   null,  NOP,   EOR,   LSR,   null,  PHA,   EOR,   LSR_A, null,  JMP,   EOR,   LSR,   null,  BVC,   EOR,   KIL,   null,  NOP,   EOR,   LSR,   null,  CLI,   EOR,   NOP,   null,  NOP,   EOR,   LSR,   null,
	/* 60 */  RTS,   ADC,   KIL,   null,  NOP,   ADC,   ROR,   null,  PLA,   ADC,   ROR_A, null,  JMP,   ADC,   ROR,   null,  BVS,   ADC,   KIL,   null,  NOP,   ADC,   ROR,   null,  SEI,   ADC,   NOP,   null,  NOP,   ADC,   ROR,   null,
	/* 80 */  NOP,   STA,   NOP,   null,  STY,   STA,   STX,   null,  DEY,   NOP,   TXA,   null,  STY,   STA,   STX,   null,  BCC,   STA,   KIL,   null,  STY,   STA,   STX,   null,  TYA,   STA,   TXS,   null,  null,  STA,   null,  null,
	/* A0 */  LDY,   LDA,   LDX,   null,  LDY,   LDA,   LDX,   null,  TAY,   LDA,   TAX,   null,  LDY,   LDA,   LDX,   null,  BCS,   LDA,   KIL,   null,  LDY,   LDA,   LDX,   null,  CLV,   LDA,   TSX,   null,  LDY,   LDA,   LDX,   null,
	/* C0 */  CPY,   CMP,   NOP,   null,  CPY,   CMP,   DEC,   null,  INY,   CMP,   DEX,   null,  CPY,   CMP,   DEC,   null,  BNE,   CMP,   KIL,   null,  NOP,   CMP,   DEC,   null,  CLD,   CMP,   NOP,   null,  NOP,   CMP,   DEC,   null,
	/* E0 */  CPX,   SBC,   NOP,   null,  CPX,   SBC,   INC,   null,  INX,   SBC,   NOP,   SBC,   CPX,   SBC,   INC,   null,  BEQ,   SBC,   KIL,   null,  NOP,   SBC,   INC,   null,  SED,   SBC,   NOP,   null,  NOP,   SBC,   INC,   null,
	};
}