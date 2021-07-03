package blue.endless.james.chip.sm83;

import blue.endless.james.host.Bus;
import blue.endless.james.host.Debug;
import blue.endless.james.host.Operand;

/**
 * <p>The heart of the GameBoy (originally called Dot Matrix Game) is the DMG-CPU.
 * 
 * <p>The DMG-CPU contains a CPU of course, but also an interrupt controller, timer, the boot rom,
 * input controller, serial port controller, sound processing unit, pixel processing unit, and
 * amazingly, some onboard memory which is neither really cache nor system RAM. This chip is an
 * early forerunner of what we'd later call SoC's.
 * 
 * <p>The other pieces of the chip - the APU, the PPU, etc. - we won't be dealing with here. This
 * class represents only the CPU portion of the SoC, which is a Sharp LR3590, hence the name.
 * 
 * <p>So if the Zilog Z80 is a strict superset of the Intel 8080, their venn diagram is one circle
 * inside the other. The Sharp LR3590 is a circle hanging off the side; it is a subset of each
 * chip but adds its own functionality not in either. This weird hybrid-but-not ISA is called
 * "Sharp SM83". We will not be reusing any 8080 or Z80 logic to implement this SM83 chip because
 * the differences are just too difficult and the gameboy has a number of interesting behavioral
 * quirks that throw extra wrenches in.
 * 
 * 
 */
public class Sm83 {
	public Sm83Registers regs = new Sm83Registers();
	public Bus bus;
	private boolean debug = false;
	//private static int presumedStackTop = 0xFFFF;
	
	private int[] instructionData = new int[4];
	public String debugString = "";
	
	public void softReset() {
		//presumedStackTop = 0xFFFF;
		regs.sp = 0xFFFE;
		//if (rom) {
			regs.pc = 0x0000;
		//} else {
			//regs.pc = 0x0100;
		//}
		
		regs.stopped = false;
	}
	
	public void hardReset() {
		softReset();
	}
	
	public int clock() {
		//Grab instruction
		regs.instructionAddress = (int) regs.pc;
		
		int op = bus.read(regs.pc) & 0xFF;
		regs.pc++;
		instructionData[0] = op;
		
		boolean cb = op==0xCB;
		if (cb) {
			op = bus.read(regs.pc) & 0xFF;
			regs.pc++;
			instructionData[1] = op;
		}
		
		SM83Opcode opcode = (cb) ? cbInstructionLogic[op] : instructionLogic[op];
		
		int opSize = (cb) ? CB_INSTRUCTION_SIZE[op] : INSTRUCTION_SIZE[op];
		
		for(int i=(cb)?2:1; i<opSize; i++) {
			instructionData[i] = bus.read(regs.pc) & 0xFF;
			regs.pc++;
		}
		
		Operand destOperand = (cb) ? cbInstructionDestOperand[op] : instructionDestOperand[op];
		Operand sourceOperand = (cb) ? cbInstructionSourceOperand[op] : instructionSourceOperand[op];
		
		int cycles = 0;
		if (opcode!=null) {
			cycles = opcode.execute(instructionData, destOperand, sourceOperand, bus, regs);
			if (debug) System.out.println(trace(instructionData));
		} else {
			System.out.println("NO INSTRUCTION LOGIC FOR 0x"+Integer.toHexString(op));
		}
		
		//if (regs.pc==0xc33b) debug = true;
		//if (regs.pc==0xc05a) debug = false;
		//if (regs.pc==0xc342) debug = true;
		
		if (instructionData[0] == 0x18 && instructionData[1] == 0xfe) {
			if (debug) System.out.println("Machine caught in intentional crash. Stopping.");
			regs.stopped = true;
		}
		
		return cycles;
	}
	
	public static boolean condition(int[] instruction, Sm83Registers regs) {
		if (instruction[0] == 0xCB) return false; //OR THROW? :)
		int hi = (instruction[0] >> 4) & 0xF;
		int lo = (instruction[0]) & 0xF;
		
		boolean zc = (hi & 0x1) == 0; // 0/true==z; 1/false==c
		boolean negate = lo < 0x8;
		
		if (zc) {
			//z
			boolean condition = regs.isSet(Sm83Registers.FLAG_ZERO);
			return (negate) ? !condition : condition;
		} else {
			//c
			boolean condition = regs.isSet(Sm83Registers.FLAG_CARRY);
			return (negate) ? !condition : condition;
		}
	}
	
	public String trace(int[] instruction) {
		int op = instruction[0];
		boolean cb = (op==0xCB);
		if (cb) op = instruction[1];
		
		String opName = (cb) ? CB_INSTRUCTION_NAME[op] : INSTRUCTION_NAME[op];
		int opSize = (cb) ? CB_INSTRUCTION_SIZE[op] : INSTRUCTION_SIZE[op];
			
		String machineCode = Debug.padRight( Debug.hexBytes(instruction, opSize), 9, ' ');
		
		Operand destOperand = (cb) ? cbInstructionDestOperand[op] : instructionDestOperand[op];
		Operand sourceOperand = (cb) ? cbInstructionSourceOperand[op] : instructionSourceOperand[op];
		
		String destTrace = (destOperand==null) ? "" : " "+destOperand.trace(instruction);
		String sourceTrace = (sourceOperand==null) ? "" : ", "+sourceOperand.trace(instruction);
		
		String trace = opName+destTrace+sourceTrace;
		trace = Debug.padRight(trace, 14, ' ');
		
		String regStatus = "bc: "+Debug.hexShort(regs.bc)+" de: "+Debug.hexShort(regs.de)+" hl: "+Debug.hexShort(regs.hl)+" af: "+Debug.hexShort(regs.af)+" sp: "+Debug.hexShort(regs.sp);
		
		return "(0x"+Debug.hexShort(regs.instructionAddress)+") "+machineCode+trace+" "+regStatus;
	}
	
	public static class ValueOperand implements Operand {
		private final int value;
		
		public ValueOperand(int value) {
			this.value = value;
		}
		
		@Override
		public int load(int[] instruction) {
			return value;
		}

		@Override
		public void store(int[] instruction, int value) {}
		
		@Override
		public String trace(int[] instruction) {
			String result = Integer.toHexString(value);
			if (result.length()==1 || result.length()==3) {
				return "%0x0"+result;
			} else {
				return "%0x"+Integer.toHexString(value);
			}
		}
	}
	
	public interface SM83Opcode {
		public int execute(int[] inst, Operand dest, Operand src, Bus bus, Sm83Registers regs);
	}
	
	public static SM83Opcode NOP = (inst, dest, src, bus, regs) -> 4; // - - - -
	public static SM83Opcode STOP = (inst, dest, src, bus, regs) -> { regs.stopped = true; return 4; }; // - - - -
	
	public static SM83Opcode LD = (inst, dest, src, bus, regs) -> {
		int value = load(inst, dest, src);
		// - - - -
		dest.store(inst, value);
		//if (dest==regs.spOperand) presumedStackTop = value;
		
		//store(inst, dest, src, value);
		postfix(dest, src);
		
		return 4;
	};
	
	public static SM83Opcode PUSH16 = (inst, dest, src, bus, regs) -> {
		int value = src.load(inst);
		// - - - -
		push16(bus, regs, value);
		
		return 16;
	};
	
	public static SM83Opcode POP16 = (inst, op1, op2, bus, regs) -> {
		int value = pop16(bus, regs);
		// - - - -
		store(inst, op1, op2, value);
		
		return 12;
	};
	
	public static SM83Opcode INC16 = (inst, op1, op2, bus, regs) -> {
		int value = (load(inst, op1, op2) + 1) & 0xFFFF;
		// - - - -
		store(inst, op1, op2, value);
		postfix(op1, op2);
		
		return 4;
	};
	
	public static SM83Opcode INC = (inst, op1, op2, bus, regs) -> {
		int value = load(inst, op1, op2);
		
		// Z 0 H -
		regs.affectZ(value+1);
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.affectH(value, 1);
		value = (value + 1) & 0xFF;
		
		store(inst, op1, op2, value);
		postfix(op1, op2);
		
		return 4;
	};
	
	public static SM83Opcode DEC16 = (inst, op1, op2, bus, regs) -> {
		int value = (load(inst, op1, op2) - 1) & 0xFFFF;
		// - - - -
		store(inst, op1, op2, value);
		postfix(op1, op2);
		
		return 4;
	};
	
	public static SM83Opcode DEC = (inst, op1, op2, bus, regs) -> {
		int value = load(inst, op1, op2);
		
		// Z 1 H -
		
		regs.set(Sm83Registers.FLAG_SUBTRACT);
		regs.affectH(value, 0xFF);
		value = (value - 1) & 0xFF;
		regs.affectZ(value);
		
		store(inst, op1, op2, value);
		postfix(op1, op2);
		
		return 4;
	};
	
	public static SM83Opcode ADD = (inst, op1, op2, bus, regs) -> {
		int m = op1.load(inst) & 0xFF;
		int n = op2.load(inst) & 0xFF;
		int value = m + n;
		
		// Z 0 H C
		regs.affectZ(value);
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.affectH(m, n);
		regs.affectC(value);
		value &= 0xFF;
		
		op1.store(inst, value);
		postfix(op1, op2);
		
		return 4;
	};
	
	public static SM83Opcode ADC = (inst, op1, op2, bus, regs) -> {
		int m = op1.load(inst) & 0xFF;
		int n = op2.load(inst) & 0xFF;
		int c = regs.isSet(Sm83Registers.FLAG_CARRY) ? 1 : 0;
		int value = m + n + c;
		
		// Z 0 H C
		regs.affectZ(value);
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.affectH(m, n, c);
		regs.affectC(value);
		value &= 0xFF;
		
		op1.store(inst, value);
		postfix(op1, op2);
		
		return 4;
	};
	
	public static SM83Opcode SUB = (inst, op1, op2, bus, regs) -> {
		int m = op1.load(inst) & 0xFF;
		int n = ( (op2==null) ? regs.getA() : op2.load(inst) ) & 0xFF;
		int value = m - n;
		
		// Z 1 H C
		regs.affectZ(value);
		regs.set(Sm83Registers.FLAG_SUBTRACT);
		regs.affectH(m, n);
		regs.affectC(value);
		value &= 0xFF;
		
		if (op2==null) {
			regs.setA(value);
		} else {
			op1.store(inst, value);
		}
		postfix(op1, op2);
		
		return 4;
	};
	
	public static SM83Opcode SBC = (inst, op1, op2, bus, regs) -> {
		int m = op1.load(inst) & 0xFF;
		int n = op2.load(inst) & 0xFF;
		int c = regs.isSet(Sm83Registers.FLAG_CARRY) ? 1 : 0;
		int value = m - n - c;
		
		// Z 0 H C
		regs.affectZ(value);
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.affectH(m, -n, -c);
		regs.affectC(value);
		value &= 0xFF;
		
		op1.store(inst, value);
		postfix(op1, op2);
		
		return 4;
	};
	
	public static SM83Opcode ADD16 = (inst, op1, op2, bus, regs) -> {
		int m = op1.load(inst);
		int n = op2.load(inst);
		int value = m + n;
		
		// - 0 H C
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.affectH16(m, n);
		regs.affectC16(value);
		value &= 0xFFFF;
		
		op1.store(inst, value);
		postfix(op1, op2);
		
		return 4;
	};
	
	public static SM83Opcode ADD16_8 = (inst, dest, src, bus, regs) -> {
		int m = dest.load(inst) & 0xFFFF;
		int n = src.load(inst) & 0xFFFF;
		int value = m + n;
		
		//System.out.println("Before; m: "+Debug.hexShort(m)+" n: "+Debug.hexShort(n)+" sp: "+Debug.hexShort(regs.sp)+" flags: "+regs.flagString());
		
		// - 0 H C
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.affectH(m, n);
		regs.affectC((m&0xFF)+(n&0xFF));
		value &= 0xFFFF;
		
		dest.store(inst, value);
		
		//System.out.println("After; sp: "+Debug.hexShort(regs.sp)+" flags: "+regs.flagString());
		
		return 4;
	};
	
	//16_8 as well, specific logic for `ld hl, sp + r8`
	public static SM83Opcode ADD_LD_HL = (inst, dest, src, bus, regs) -> {
		int m = dest.load(inst) & 0xFFFF; //e.g. sp
		int n = src.load(inst) & 0xFFFF; //e.g. r8
		int value = m + n;
		
		// 0 0 H C
		regs.clear(Sm83Registers.FLAG_ZERO);
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.affectH(m, n);
		regs.affectC((m&0xFF)+(n&0xFF));
		
		value &= 0xFFFF;
		
		regs.hlOperand.store(inst, value);
		
		return 12;
	};
	
	
	public static SM83Opcode AND = (inst, op1, op2, bus, regs) -> {
		int value = load(inst, op1, op2) & regs.getA();
		
		// Z 0 1 0
		// Why does it set half-carry? I don't know!
		regs.affectZ(value);
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.set(Sm83Registers.FLAG_HALF_CARRY);
		regs.clear(Sm83Registers.FLAG_CARRY);
		
		regs.setA(value);
		postfix(op1, op2);
		
		return 4;
	};
	
	public static SM83Opcode XOR = (inst, op1, op2, bus, regs) -> {
		int value = load(inst, op1, op2) ^ regs.getA();
		
		// Z 0 0 0
		// Why does it set half-carry? I don't know!
		regs.affectZ(value);
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.clear(Sm83Registers.FLAG_HALF_CARRY);
		regs.clear(Sm83Registers.FLAG_CARRY);
		
		regs.setA(value);
		postfix(op1, op2);
		
		return 4;
	};
	
	public static SM83Opcode OR = (inst, op1, op2, bus, regs) -> {
		int value = load(inst, op1, op2) | regs.getA();
		
		// Z 0 1 0
		// Why does it set half-carry? I don't know!
		regs.affectZ(value);
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.clear(Sm83Registers.FLAG_HALF_CARRY);
		regs.clear(Sm83Registers.FLAG_CARRY);
		
		regs.setA(value);
		postfix(op1, op2);
		
		return 4;
	};
	
	public static SM83Opcode BIT = (inst, dest, src, bus, regs) -> {
		int bit = src.load(inst);
		int value = dest.load(inst);
		
		int bitmask = (0x01 << bit) & 0xFFFF;
		boolean maskedBit = (value & bitmask) != 0;
		
		// Z 0 1 -
		regs.set(Sm83Registers.FLAG_ZERO, !maskedBit); // The bit is inverted; if the bit is set, zero is CLEARED, and if the bit is clear, zero is SET
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.set(Sm83Registers.FLAG_HALF_CARRY);
		
		dest.postfix();
		
		return 8;
	};
	
	public static SM83Opcode SET = (inst, dest, src, bus, regs) -> {
		int bit = src.load(inst);
		int value = dest.load(inst);
		
		int bitmask = (0x01 << bit) & 0xFFFF;
		value |= bitmask;
		// - - - -
		
		dest.store(inst, value);
		dest.postfix();
		
		return 8;
	};
	
	public static SM83Opcode RES = (inst, dest, src, bus, regs) -> {
		int bit = src.load(inst);
		int value = dest.load(inst);
		
		int bitmask = (0x01 << bit) & 0xFFFF;
		value &= (~bitmask) & 0xFF;
		// - - - -
		
		dest.store(inst, value);
		dest.postfix();
		
		return 8;
	};
	
	public static SM83Opcode RLCA = (inst, op1, op2, bus, regs) -> {
		int value = regs.getA();
		boolean c = (value & 0x80) != 0;
		
		// 0 0 0 C
		regs.clear(Sm83Registers.FLAG_ZERO);
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.clear(Sm83Registers.FLAG_HALF_CARRY);
		regs.set(Sm83Registers.FLAG_CARRY, c);
		//Roll it
		value = (value << 1) & 0xFF;
		if (c) value |= 1;
		
		regs.setA(value);
		
		return 4;
	};
	
	/** Unline RLCA, the new and old carry are distinct; you've got to CLC before you use this to scrub through bits! */
	public static SM83Opcode RLA = (inst, op1, op2, bus, regs) -> {
		int value = regs.getA();
		boolean oldCarry = regs.isSet(Sm83Registers.FLAG_CARRY);
		boolean newCarry = (value & 0x80) != 0;
		
		// 0 0 0 C
		regs.clear(Sm83Registers.FLAG_ZERO);
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.clear(Sm83Registers.FLAG_HALF_CARRY);
		regs.set(Sm83Registers.FLAG_CARRY, newCarry);
		//Roll it
		value = (value << 1) & 0xFF;
		if (oldCarry) value |= 1;
		
		regs.setA(value);
		
		return 4;
	};
	
	public static SM83Opcode RRCA = (inst, op1, op2, bus, regs) -> {
		int value = regs.getA();
		boolean c = (value & 0x01) != 0;
		
		// 0 0 0 C
		regs.clear(Sm83Registers.FLAG_ZERO);
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.clear(Sm83Registers.FLAG_HALF_CARRY);
		regs.set(Sm83Registers.FLAG_CARRY, c);
		//Roll it
		value = (value >> 1) & 0xFF;
		if (c) value |= 0x80;
		
		regs.setA(value);
		
		return 4;
	};
	
	/** Unline RRCA, the new and old carry are distinct; you've got to CLC before you use this to scrub through bits! */
	public static SM83Opcode RRA = (inst, op1, op2, bus, regs) -> {
		int value = regs.getA();
		boolean oldCarry = regs.isSet(Sm83Registers.FLAG_CARRY);
		boolean newCarry = (value & 0x01) != 0;
		
		// 0 0 0 C
		regs.clear(Sm83Registers.FLAG_ZERO);
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.clear(Sm83Registers.FLAG_HALF_CARRY);
		regs.set(Sm83Registers.FLAG_CARRY, newCarry);
		//Roll it
		value = (value >> 1) & 0xFF;
		if (oldCarry) value |= 0x80;
		
		regs.setA(value);
		
		return 4;
	};
	
	/** Decimal Adjust Accumulator; "Does cool stuff with binary-coded decimal" */
	public static SM83Opcode DAA = (inst, op1, op2, bus, regs) -> {
		//System.out.println("(0x"+Debug.hexShort(regs.instructionAddress)+"): DAA");
		int value = regs.getA() & 0xFF;
		//System.out.println("input: 0x"+Debug.hexByte(value));
		if (!regs.isSet(Sm83Registers.FLAG_SUBTRACT)) {
			//Adjust binary addition of BCD numbers to make the result back into BCD
			if (regs.isSet(Sm83Registers.FLAG_CARRY) || value>0x99) {
				value += 0x60;
				regs.set(Sm83Registers.FLAG_CARRY);
			}
			if (regs.isSet(Sm83Registers.FLAG_HALF_CARRY) || ((value&0x0F)>0x09)) {
				value += 0x06;
			}
			
			regs.setA(value & 0xFF);
		} else {
			//Adjust binary subtraction of BCD numbers to make the result back into BCD
			if (regs.isSet(Sm83Registers.FLAG_CARRY)) {
				value -= 0x60;
			}
			if (regs.isSet(Sm83Registers.FLAG_HALF_CARRY)) {
				value -= 0x06;
			}
			
			regs.setA(value & 0xFF);
		}
		
		// Z - 0 C
		regs.clear(Sm83Registers.FLAG_HALF_CARRY);
		regs.set(Sm83Registers.FLAG_ZERO, (value & 0xFF)==0);
		
		
		return 4;
	};
	
	public static SM83Opcode RLC = (inst, dest, src, bus, regs) -> {
		int value = src.load(inst);
		boolean c = (value & 0x80) != 0;
		
		// Z 0 0 C
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.clear(Sm83Registers.FLAG_HALF_CARRY);
		Sm83Registers.FLAG_CARRY.set(regs.af, c);
		//Roll it
		value = (value << 1) & 0xFF;
		if (c) value |= 1;
		
		regs.affectZ(value);
		
		dest.store(inst, value);
		postfix(dest, src);
		
		return 8;
	};
	
	public static SM83Opcode RRC = (inst, dest, src, bus, regs) -> {
		int value = src.load(inst);
		boolean c = (value & 0x01) != 0;
		
		// Z 0 0 C
		
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.clear(Sm83Registers.FLAG_HALF_CARRY);
		Sm83Registers.FLAG_CARRY.set(regs.af, c);
		//Roll it
		value = (value >> 1) & 0xFF;
		if (c) value |= 0x80;
		
		regs.affectZ(value);
		
		dest.store(inst, value);
		dest.postfix();
		
		return 8;
	};
	
	public static SM83Opcode RL = (inst, dest, src, bus, regs) -> {
		int value = src.load(inst);
		boolean oldCarry = regs.isSet(Sm83Registers.FLAG_CARRY);
		boolean newCarry = (value & 0x80) != 0;
		
		// Z 0 0 C
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.clear(Sm83Registers.FLAG_HALF_CARRY);
		regs.set(Sm83Registers.FLAG_CARRY, newCarry);
		//Roll it
		value = (value << 1) & 0xFF;
		if (oldCarry) value |= 1;
		
		regs.set(Sm83Registers.FLAG_ZERO, value==0);
		
		dest.store(inst, value);
		dest.postfix();
		
		return 8;
	};
	
	public static SM83Opcode RR = (inst, dest, src, bus, regs) -> {
		int value = src.load(inst);
		boolean oldCarry = regs.isSet(Sm83Registers.FLAG_CARRY);
		boolean newCarry = (value & 0x01) != 0;
		
		// Z 0 0 C
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.clear(Sm83Registers.FLAG_HALF_CARRY);
		regs.set(Sm83Registers.FLAG_CARRY, newCarry);
		//Roll it
		value = (value >> 1) & 0xFF;
		if (oldCarry) value |= 0x80;
		
		regs.set(Sm83Registers.FLAG_ZERO, value==0);
		
		dest.store(inst, value);
		dest.postfix();
		
		return 8;
	};
	
	/** Arithmetic Shift Left - bit 7 goes to the carry, bit zero becomes zero */
	public static SM83Opcode SLA = (inst, op1, op2, bus, regs) -> {
		int value = load(inst, op1, op2);
		boolean newCarry = (value & 0x80) != 0;
		
		// Z 0 0 C
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.clear(Sm83Registers.FLAG_HALF_CARRY);
		regs.set(Sm83Registers.FLAG_CARRY, newCarry);
		//Roll it
		value = (value << 1) & 0xFF;
		
		regs.set(Sm83Registers.FLAG_ZERO, value==0);
		
		store(inst, op1, op2, value);
		postfix(op1, op2);
		
		return 8;
	};
	
	/** Arithmetic Shift Right - bit 1 goes to the carry, bit 7 sign-extends */
	public static SM83Opcode SRA = (inst, op1, op2, bus, regs) -> {
		int value = load(inst, op1, op2);
		boolean newCarry = (value & 0x01) != 0;
		int oldBit7 = value & 0x80;
		
		// Z 0 0 C
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.clear(Sm83Registers.FLAG_HALF_CARRY);
		regs.set(Sm83Registers.FLAG_CARRY, newCarry);
		//Roll it
		value = (value >> 1) & 0xFF;
		value |= oldBit7;
		
		regs.set(Sm83Registers.FLAG_ZERO, value==0);
		
		store(inst, op1, op2, value);
		postfix(op1, op2);
		
		return 8;
	};
	
	/** Logical Shift Right - bit 1 goes to the carry, bit 7 becomes zero */
	public static SM83Opcode SRL = (inst, op1, op2, bus, regs) -> {
		int value = load(inst, op1, op2);
		boolean newCarry = (value & 0x01) != 0;
		
		// Z 0 0 C
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.clear(Sm83Registers.FLAG_HALF_CARRY);
		regs.set(Sm83Registers.FLAG_CARRY, newCarry);
		//Roll it
		value = (value >> 1) & 0xFF;
		
		regs.set(Sm83Registers.FLAG_ZERO, value==0);
		
		store(inst, op1, op2, value);
		postfix(op1, op2);
		
		return 8;
	};
	
	public static SM83Opcode SWAP = (inst, op1, op2, bus, regs) -> {
		int value = load(inst, op1, op2);
		
		int low = value & 0xF;
		int high = (value >> 4) & 0xF;
		value = (low << 4) | high;
		
		// Z 0 0 0
		regs.set(Sm83Registers.FLAG_ZERO, value==0);
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.clear(Sm83Registers.FLAG_HALF_CARRY);
		regs.clear(Sm83Registers.FLAG_CARRY);
		
		store(inst, op1, op2, value);
		postfix(op1, op2);
		
		return 8;
	};
	
	/** It says "Set Carry Flag" on the tin but also clears subtract and halfcarry. */
	public static SM83Opcode SCF = (inst, op1, op2, bus, regs) -> {
		regs.set(Sm83Registers.FLAG_CARRY);
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.clear(Sm83Registers.FLAG_HALF_CARRY);
		
		return 4;
	};
	
	/** It says "Clear Carry Flag" on the tin but also clears subtract and halfcarry. And also flips the carry flag instead of clearing it. */
	public static SM83Opcode CCF = (inst, op1, op2, bus, regs) -> {
		regs.set(Sm83Registers.FLAG_CARRY, !regs.isSet(Sm83Registers.FLAG_CARRY));
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.clear(Sm83Registers.FLAG_HALF_CARRY);
		
		return 4;
	};
	
	/** "Complement" */
	public static SM83Opcode CPL = (inst, op1, op2, bus, regs) -> {
		regs.setA(~regs.getA());
		// - 0 0 -
		regs.clear(Sm83Registers.FLAG_SUBTRACT);
		regs.clear(Sm83Registers.FLAG_HALF_CARRY);
		
		return 4;
	};
	
	
	/** "CP" in the official sharp and z80 docs, but "CMP" in 6802, 68k, and x86 */
	public static SM83Opcode CMP = (inst, dest, src, bus, regs) -> {
		int m = dest.load(inst) & 0xFF;
		int n = src.load(inst) & 0xFF;
		//negate n with Bitwise Magic(tm)
		int nInverse = ((~n) + 1) & 0xFF;
		
		int value = m - n;
		
		if (inst[0]==0xFE) {
			//System.out.println("Before; m: "+Debug.hexByte(m)+" n: "+Debug.hexByte(n)+" value: "+Debug.hexByte(value)+" flags: "+regs.flagString());
		}
		
		// Z 1 H C
		regs.affectZ(value & 0xFF);
		regs.set(Sm83Registers.FLAG_SUBTRACT);
		regs.affectH(m, nInverse);
		regs.affectC(value & 0x1FF);
		value &= 0xFF;
		
		//in SUB we'd store here but we're not SUB
		postfix(dest, src);
		
		if (inst[0]==0xFE) {
			//System.out.println("After; flags: "+regs.flagString());
		}
		
		return 4;
	};
	
	private SM83Opcode JMP = (inst, dest, src, bus, regs) -> {
		int value = dest.load(inst) & 0xFFFF;
		regs.pc = value;
		
		// - - - -
		
		return 16;
	};
	
	private SM83Opcode JMP_C = (inst, op1, op2, bus, regs) -> {
		
		// - - - -
		
		if (condition(inst, regs)) {
			int value = load(inst, op1, op2) & 0xFFFF;
			
			regs.pc = value;
			return 16;
		} else {
			return 12;
		}
	};
	
	public static SM83Opcode CALL = (inst, op1, op2, bus, regs) -> {
		
		// - - - -
		
		int value = load(inst, op1, op2) & 0xFFFF;
		postfix(op1, op2);
		
		push16(bus, regs, regs.pc);
		regs.pc = value;
		
		return 24;
	};
	
	public static SM83Opcode CALL_C = (inst, op1, op2, bus, regs) -> {
		
		// - - - -
		
		if (condition(inst, regs)) {
			int value = load(inst, op1, op2) & 0xFFFF;
			push16(bus, regs, regs.pc);
			regs.pc = value;
			return 24;
		} else {
			return 12;
		}
	};
	
	public static SM83Opcode EI = (inst, op1, op2, bus, regs) -> {
		
		// - - - -
		
		regs.enableInterrupts = true;
		return 4;
	};
	
	public static SM83Opcode DI = (inst, op1, op2, bus, regs) -> {
		
		// - - - -
		
		regs.enableInterrupts = false;
		return 4;
	};
	
	public static SM83Opcode RETI = (inst, op1, op2, bus, regs) -> {
		
		// - - - -
		
		int value = pop16(bus, regs);
		regs.pc = value;
		regs.enableInterrupts = true;
		return 16;
	};
	
	public static SM83Opcode RET = (inst, op1, op2, bus, regs) -> {
		
		// - - - -
		
		int value = pop16(bus, regs);
		regs.pc = value;
		return 16;
	};
	

	public static SM83Opcode RET_C = (inst, op1, op2, bus, regs) -> {
		
		// - - - -
		
		if (condition(inst, regs)) {
			int value = pop16(bus, regs);
			regs.pc = value;
			return 20;
		} else {
			return 8;
		}
	};
	
	/** "Reset" to a system function. These reside at 00, 08, 10, 18, 20, 28, 30, and 38. Since these are mapped to cartridge rom, they're normally unused in the gameboy. */
	public static SM83Opcode RST = (inst, op1, op2, bus, regs) -> {
		int value = load(inst, op1, op2) & 0xFFFF;
		postfix(op1, op2);
		
		push16(bus, regs, regs.pc);
		regs.pc = value;
		
		// - - - -
		
		return 16;
	};
	
	private static final int load(int[] inst, Operand op1, Operand op2) {
		if (op2!=null) return op2.load(inst);
		return op1.load(inst);
	}
	
	private static final void store(int[] inst, Operand op1, Operand op2, int val) {
		op1.store(inst, val);
	}
	
	private static final void postfix(Operand op1, Operand op2) {
		if (op2!=null) op2.postfix();
		if (op1!=null) op1.postfix();
	}
	
	private static final void push16(Bus bus, Sm83Registers regs, int value) {
		int lo = value & 0xFF;
		int hi = (value >> 8) & 0xFF;
		push8(bus, regs, hi);
		push8(bus, regs, lo);
	}
	
	private static final int pop16(Bus bus, Sm83Registers regs) {
		int lo = pop8(bus, regs);
		int hi = pop8(bus, regs);
		int value = (hi << 8) | lo;
		return value;
	}
	
	private static final void push8(Bus bus, Sm83Registers regs, int value) {
		regs.sp = (regs.sp - 1) & 0xFFFF;
		bus.write(regs.sp, value & 0xFF);
	}
	
	private static final int pop8(Bus bus, Sm83Registers regs) {
		int value = bus.read(regs.sp) & 0xFF;
		regs.sp = (regs.sp + 1) & 0xFFFF;
		return value;
	}
	
	public Operand a = regs.aOperand;
	public Operand f = regs.fOperand;
	public Operand b = regs.bOperand;
	public Operand c = regs.cOperand;
	public Operand d = regs.dOperand;
	public Operand e = regs.eOperand;
	public Operand h = regs.hOperand;
	public Operand l = regs.lOperand;
	public Operand af = regs.afOperand;
	public Operand bc = regs.bcOperand;
	public Operand de = regs.deOperand;
	public Operand hl = regs.hlOperand;
	public Operand sp = regs.spOperand;
	
	public ValueOperand r00 = new ValueOperand(0x00);
	public ValueOperand r08 = new ValueOperand(0x08);
	public ValueOperand r10 = new ValueOperand(0x10);
	public ValueOperand r18 = new ValueOperand(0x18);
	public ValueOperand r20 = new ValueOperand(0x20);
	public ValueOperand r28 = new ValueOperand(0x28);
	public ValueOperand r30 = new ValueOperand(0x30);
	public ValueOperand r38 = new ValueOperand(0x38);
	
	public ValueOperand v0 = new ValueOperand(0);
	public ValueOperand v1 = new ValueOperand(1);
	public ValueOperand v2 = new ValueOperand(2);
	public ValueOperand v3 = new ValueOperand(3);
	public ValueOperand v4 = new ValueOperand(4);
	public ValueOperand v5 = new ValueOperand(5);
	public ValueOperand v6 = new ValueOperand(6);
	public ValueOperand v7 = new ValueOperand(7);
	
	public Operand c_i = new Operand() {
		@Override
		public int load(int[] instruction) {
			int ind = c.load(instruction) | 0xFF00;
			return bus.read(ind);
		}
		
		@Override
		public void store(int[] instruction, int value) {
			int ind = c.load(instruction) | 0xFF00;
			bus.write(ind, value & 0xFF);
		}
		
		@Override
		public String trace(int[] instruction) { return "(bc)"; }
	};
	
	public Operand bc_i = new Operand() {
		@Override
		public int load(int[] instruction) {
			int ind = bc.load(instruction);
			return bus.read(ind);
		}
		
		@Override
		public void store(int[] instruction, int value) {
			int ind = bc.load(instruction);
			bus.write(ind, value & 0xFF);
		}
		
		@Override
		public String trace(int[] instruction) { return "(bc)"; }
	};
	
	public Operand de_i = new Operand() {
		@Override
		public int load(int[] instruction) {
			int ind = de.load(instruction);
			return bus.read(ind);
		}
		
		@Override
		public void store(int[] instruction, int value) {
			int ind = de.load(instruction);
			bus.write(ind, value & 0xFF);
		}
		
		@Override
		public String trace(int[] instruction) { return "(de)"; }
	};
	
	public Operand hl_i = new Operand() {
		@Override
		public int load(int[] instruction) {
			int ind = hl.load(instruction);
			return bus.read(ind);
		}
		
		@Override
		public void store(int[] instruction, int value) {
			int ind = hl.load(instruction);
			bus.write(ind, value & 0xFF);
		}
		
		@Override
		public String trace(int[] instruction) { return "(hl)"; }
	};
	
	public Operand hl_ip = new Operand() {
		@Override
		public int load(int[] instruction) {
			int ind = hl.load(instruction);
			return bus.read(ind);
		}
		
		@Override
		public void store(int[] instruction, int value) {
			int ind = hl.load(instruction);
			bus.write(ind, value & 0xFF);
		}
		
		@Override
		public String trace(int[] instruction) { return "(hl+)"; }
		
		@Override
		public void postfix() {
			regs.hl = (regs.hl + 1) & 0xFFFF;
		}
	};
	
	public Operand hl_im = new Operand() {
		@Override
		public int load(int[] instruction) {
			int ind = hl.load(instruction);
			return bus.read(ind);
		}
		
		@Override
		public void store(int[] instruction, int value) {
			int ind = hl.load(instruction);
			bus.write(ind, value & 0xFF);
		}
		
		@Override
		public String trace(int[] instruction) { return "(hl-)"; }
		
		@Override
		public void postfix() {
			regs.hl = (regs.hl - 1) & 0xFFFF;
		}
	};
	
	/**
	 * For symmetry with other jump instructions, "load" loads the address to set PC to
	 */
	public Operand rel = new Operand() {
		@Override
		public int load(int[] instruction) {
			int relative = addr(instruction);
			return (regs.pc + relative) & 0xFFFF;
		}

		@Override
		public void store(int[] instruction, int value) {}
		
		@Override
		public String trace(int[] instruction) {
			int relative = addr(instruction);
			if (relative<0) return ""+relative;
			else return "+"+relative;
		};
		
		private int addr(int[] instruction) {
			int relative = instruction[1] & 0xFF;
			if ((relative & 0x80) != 0) relative |= ~0xFF; //sign-extend
			return relative;
		}
	};
	
	public Operand r8 = new Operand() {
		@Override
		public int load(int[] instruction) {
			return addr(instruction);
		}

		@Override
		public void store(int[] instruction, int value) {}
		
		@Override
		public String trace(int[] instruction) {
			int relative = addr(instruction);
			if (relative<0) return ""+relative;
			else return "+"+relative;
		};
		
		private int addr(int[] instruction) {
			int relative = instruction[1] & 0xFF;
			if ((relative & 0x80) != 0) relative |= ~0xFF; //sign-extend
			return relative;
		}
	};
	
	public Operand a8 = new Operand() {
		@Override
		public int load(int[] instruction) {
			return instruction[1] & 0xFF;
		}
		
		@Override
		public void store(int[] instruction, int value) {
			//TODO: Halt the machine? Nothing? Can't store to an immediate value!
		}
		
		@Override
		public String trace(int[] instruction) {
			int value = instruction[1] & 0xFF;
			String result = Integer.toHexString(value);
			while (result.length()<2) result = "0"+result;
			return "0x"+result;
		}
	};
	
	public Operand a8_i = new Operand() {
		@Override
		public int load(int[] instruction) {
			int addr = (instruction[1] & 0xFF) | 0xFF00;
			return bus.read(addr) & 0xFF;
		}
		
		@Override
		public void store(int[] instruction, int value) {
			int addr = (instruction[1] & 0xFF) | 0xFF00;
			bus.write(addr, value & 0xFF);
		}
		
		@Override
		public String trace(int[] instruction) {
			int value = instruction[1] & 0xFF;
			String result = Integer.toHexString(value);
			while (result.length()<2) result = "0"+result;
			return "(0xff00 + 0x"+result+")";
		}
	};
	
	public Operand a16 = new Operand() {
		@Override
		public int load(int[] instruction) {
			int low = instruction[1] & 0xFF;
			int high = instruction[2] & 0xFF;
			int value = (high << 8) | low;
			return value;
		}
		
		@Override
		public void store(int[] instruction, int value) {
			//TODO: Halt the machine? Nothing? Can't store to an immediate value!
		}
		
		@Override
		public String trace(int[] instruction) {
			int low = instruction[1] & 0xFF;
			int high = instruction[2] & 0xFF;
			int value = (high << 8) | low;
			String result = Integer.toHexString(value);
			while (result.length()<4) result = "0"+result;
			return "0x"+result;
		}
	};
	
	public Operand a16_i = new Operand() {
		@Override
		public int load(int[] instruction) {
			int ind = a16.load(instruction);
			return bus.read(ind) & 0xFF;
		}
		
		@Override
		public void store(int[] instruction, int value) {
			int ind = a16.load(instruction);
			bus.write(ind, value & 0xFF);
		}
		
		@Override
		public String trace(int[] instruction) {
			int low = instruction[1] & 0xFF;
			int high = instruction[2] & 0xFF;
			int value = (high << 8) | low;
			String result = Integer.toHexString(value);
			while (result.length()<4) result = "0"+result;
			return "(0x"+result+")";
		}
	};
	
	public Operand a16i16 = new Operand() {
		@Override
		public int load(int[] instruction) {
			int ind = a16.load(instruction);
			int valLow = bus.read(ind) & 0xFF;
			ind = (ind + 1) & 0xFFFF;
			int valHigh = bus.read(ind) & 0xFF;
			return (valHigh << 8) | valLow;
		}
		
		@Override
		public void store(int[] instruction, int value) {
			int ind = a16.load(instruction);
			bus.write(ind, value & 0xFF);
			ind = (ind + 1) & 0xFFFF;
			value = (value >> 8) & 0xFF;
			bus.write(ind, value);
		}
		
		@Override
		public String trace(int[] instruction) {
			int low = instruction[1] & 0xFF;
			int high = instruction[2] & 0xFF;
			int value = (high << 8) | low;
			String result = Integer.toHexString(value);
			while (result.length()<4) result = "0"+result;
			return "(0x"+result+")";
		}
	};
	
	public static final String[] INSTRUCTION_NAME = {
	// thanks to https://www.pastraiser.com/cpu/gameboy/gameboy_opcodes.html for the concise table
	/*        00      01     02     03     04     05     06     07     08     09     0A     0B     0C     0D     0E     0F */
	/* 00 */ "nop",  "ld",  "ld",  "inc", "inc", "dec", "ld",  "rlc", "ld",  "add", "ld",  "dec", "inc", "dec", "ld",  "RRCA",
	/* 10 */ "stop", "ld",  "ld",  "inc", "inc", "dec", "ld",  "RLA", "jmp", "add", "ld",  "dec", "inc", "dec", "ld",  "RCA",
	/* 20 */ "jnz",  "ld",  "ld",  "inc", "inc", "dec", "ld",  "DAA", "jz",  "add", "ld",  "dec", "inc", "dec", "ld",  "CPL",
	/* 30 */ "jnc",  "ld",  "ld",  "inc", "inc", "dec", "ld",  "SCF", "jc",  "add", "ld",  "dec", "inc", "dec", "ld",  "CCF",
	/* 40 */ "ld",   "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",
	/* 50 */ "ld",   "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",
	/* 60 */ "ld",   "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",
	/* 70 */ "ld",   "ld",  "ld",  "ld",  "ld",  "ld",  "halt","ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",  "ld",
	/* 80 */ "add",  "add", "add", "add", "add", "add", "add", "add", "adc", "adc", "adc", "adc", "adc", "adc", "adc", "adc",
	/* 90 */ "sub",  "sub", "sub", "sub", "sub", "sub", "sub", "sub", "sbc", "sbc", "sbc", "sbc", "sbc", "sbc", "sbc", "sbc",
	/* A0 */ "and",  "and", "and", "and", "and", "and", "and", "and", "xor", "xor", "xor", "xor", "xor", "xor", "xor", "xor",
	/* B0 */ "or",   "or",  "or",  "or",  "or",  "or",  "or",  "or",  "cmp", "cmp", "cmp", "cmp", "cmp", "cmp", "cmp", "cmp",
	/* C0 */ "retnz","pop", "jnz", "jmp", "call","push","add", "rst", "ret", "ret", "jmp", ":CB:","call","call","adc", "rst",
	/* D0 */ "retnc","pop", "jnc", null,  "call","push","sub", "rst", "ret", "reti","jmp", null,  "call",null,  "sbc", "rst",
	/* E0 */ "ld",   "pop", "ld",  null,  null,  "push","and", "rst", "add", "jmp", "ld",  null,  null,  null,  "xor", "rst",
	/* F0 */ "ld",   "pop", "ld",  "di",  null,  "push","or",  "rst", "ld",  "ld",  "ld",  "EI",  null,  null,  "cmp", "rst",
	};
	
	/** Size of each instruction in bytes, including the opcode byte */
	public static final int[] INSTRUCTION_SIZE = {
	// Zeroes indicate illegal opcode slots which will (probably) hard-crash the CPU
	// CB is a special opcode slot which indicates that we should proceed to the prefix-CB table below
	/* $10 'STOP 0' is listed as '$10 $00', and 2 bytes here, probably because this processor is pipelined,
	 * so during the last (4th) cycle of the STOP instruction, a byte is fetched for the next instruction.
	 * It's best if this byte is specifically reserved as a NOP because **a read will always occur for this
	 * byte**.
	 */
	/*        00     01     02     03     04     05     06     07     08     09     0A     0B     0C     0D     0E     0F */
	/* 00 */   1,     3,     1,     1,     1,     1,     2,     1,     3,     1,     1,     1,     1,     1,     2,     1,
	/* 10 */   2,     3,     1,     1,     1,     1,     2,     1,     2,     1,     1,     1,     1,     1,     2,     1,
	/* 20 */   2,     3,     1,     1,     1,     1,     2,     1,     2,     1,     1,     1,     1,     1,     2,     1,
	/* 30 */   2,     3,     1,     1,     1,     1,     2,     1,     2,     1,     1,     1,     1,     1,     2,     1,
	/* 40 */   1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,
	/* 50 */   1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,
	/* 60 */   1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,
	/* 70 */   1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,
	/* 80 */   1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,
	/* 90 */   1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,
	/* A0 */   1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,
	/* B0 */   1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1,
	/* C0 */   1,     1,     3,     3,     3,     1,     2,     1,     1,     1,     3,     1,     3,     3,     2,     1,
	/* D0 */   1,     1,     3,     0,     3,     1,     2,     1,     1,     1,     3,     0,     3,     0,     2,     1,
	/* E0 */   2,     1,     1,     0,     0,     1,     2,     1,     2,     1,     3,     0,     0,     0,     2,     1,
	/* F0 */   2,     1,     2,     1,     0,     1,     2,     1,     2,     1,     3,     1,     0,     0,     2,     1,
	};
	
	public final SM83Opcode[] instructionLogic = {
	/*        00     01     02     03     04     05     06     07     08     09     0A     0B     0C     0D     0E     0F */
	/* 00 */  NOP,   LD,    LD,    INC16, INC,   DEC,   LD,    RLCA,  LD,    ADD16, LD,    DEC16, INC,   DEC,   LD,    RRCA,
	/* 10 */  STOP,  LD,    LD,    INC16, INC,   DEC,   LD,    RLA,   JMP,   ADD16, LD,    DEC16, INC,   DEC,   LD,    RRA,
	/* 20 */  JMP_C, LD,    LD,    INC16, INC,   DEC,   LD,    DAA,   JMP_C, ADD16, LD,    DEC16, INC,   DEC,   LD,    CPL,
	/* 30 */  JMP_C, LD,    LD,    INC16, INC,   DEC,   LD,    SCF,   JMP_C, ADD16, LD,    DEC16, INC,   DEC,   LD,    CCF,
	/* 40 */  LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,
	/* 50 */  LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,
	/* 60 */  LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,
	/* 70 */  LD,    LD,    LD,    LD,    LD,    LD,    STOP,  LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,    LD,
	/* 80 */  ADD,   ADD,   ADD,   ADD,   ADD,   ADD,   ADD,   ADD,   ADC,   ADC,   ADC,   ADC,   ADC,   ADC,   ADC,   ADC,
	/* 90 */  SUB,   SUB,   SUB,   SUB,   SUB,   SUB,   SUB,   SUB,   SBC,   SBC,   SBC,   SBC,   SBC,   SBC,   SBC,   SBC,
	/* A0 */  AND,   AND,   AND,   AND,   AND,   AND,   AND,   AND,   XOR,   XOR,   XOR,   XOR,   XOR,   XOR,   XOR,   XOR,
	/* B0 */  OR,    OR,    OR,    OR,    OR,    OR,    OR,    OR,    CMP,   CMP,   CMP,   CMP,   CMP,   CMP,   CMP,   CMP,
	/* C0 */  RET_C, POP16, JMP_C, JMP,   CALL_C,PUSH16,ADD,   RST,   RET_C, RET,   JMP_C, null,  CALL_C,CALL,  ADC,   RST,
	/* D0 */  RET_C, POP16, JMP_C, null,  CALL_C,PUSH16,SUB,   RST,   RET_C, RETI,  JMP_C, null,  CALL_C,null,  SBC,   RST,
	/* E0 */  LD,    POP16, LD,    null,  null,  PUSH16,AND,   RST,  ADD16_8,JMP,   LD,    null,  null,  null,  XOR,   RST,
	/* F0 */  LD,    POP16, LD,    DI,    null,  PUSH16,OR,    RST,ADD_LD_HL,LD,    LD,    EI,    null,  null,  CMP,   RST,
	};
	
	public final Operand[] instructionDestOperand = {
	/*        00     01     02     03     04     05     06     07     08     09     0A     0B     0C     0D     0E     0F */
	/* 00 */  null,  bc,    bc_i,  bc,    b,     b,     b,     a,     a16i16,hl,    a,     bc,    c,     c,     c,     a,
	/* 10 */  null,  de,    de_i,  de,    d,     d,     d,     a,     rel,   hl,    a,     de,    e,     e,     e,     a,
	/* 20 */  rel,   hl,    hl_ip, hl,    h,     h,     h,     a,     rel,   hl,    a,     hl,    l,     l,     l,     null,
	/* 30 */  rel,   sp,    hl_im, sp,    hl_i,  hl_i,  hl_i,  null,  rel,   hl,    a,     sp,    a,     a,     a,     null,
	/* 40 */  b,     b,     b,     b,     b,     b,     b,     b,     c,     c,     c,     c,     c,     c,     c,     c,
	/* 50 */  d,     d,     d,     d,     d,     d,     d,     d,     e,     e,     e,     e,     e,     e,     e,     e,
	/* 60 */  h,     h,     h,     h,     h,     h,     h,     h,     l,     l,     l,     l,     l,     l,     l,     l,
	/* 70 */  hl_i,  hl_i,  hl_i,  hl_i,  hl_i,  hl_i,  null,  hl_i,  a,     a,     a,     a,     a,     a,     a,     a,
	/* 80 */  a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,
	/* 90 */  a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,
	/* A0 */  a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,
	/* B0 */  a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,     a,
	/* C0 */  null,  bc,    a16,   a16,   a16,   null,  a,     r00,   null,  null,  a16,   null,  a16,   a16,   a,     r08,
	/* D0 */  null,  de,    a16,   null,  a16,   null,  a,     r10,   null,  null,  a16,   null,  a16,   null,  a,     r18,
	/* E0 */  a8_i,  hl,    c_i,   null,  null,  null,  a,     r20,   sp,    hl,    a16_i, null,  null,  null,  a,     r28,
	/* F0 */  a,     af,    a,     null,  null,  null,  a,     r30,   sp,    sp,    a,     null,  null,  null,  a,     r38,
	};
	
	public final Operand[] instructionSourceOperand = {
	/*        00     01     02     03     04     05     06     07     08     09     0A     0B     0C     0D     0E     0F */
	/* 00 */  null,  a16,   a,     bc,    b,     b,     a8,    a,     sp,    bc,    bc_i,  bc,    c,     c,     a8,    a,
	/* 10 */  null,  a16,   a,     de,    d,     d,     a8,    a,     null,  de,    de_i,  de,    e,     e,     a8,    a,
	/* 20 */  null,  a16,   a,     hl,    h,     h,     a8,    a,     null,  hl,    hl_ip, hl,    l,     l,     a8,    null,
	/* 30 */  null,  a16,   a,     sp,    hl_i,  hl_i,  a8,    null,  null,  sp,    hl_im, sp,    a,     a,     a8,    null,
	/* 40 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* 50 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* 60 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* 70 */  b,     c,     d,     e,     h,     l,     null,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* 80 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* 90 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* A0 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* B0 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* C0 */  null,  null,  null,  null,  null,  bc,    a8,    null,  null,  null,  null,  null,  null,  null,  a8,    null,
	/* D0 */  null,  null,  null,  null,  null,  de,    a8,    null,  null,  null,  null,  null,  null,  null,  a8,    null,
	/* E0 */  a,     null,  a,     null,  null,  hl,    a8,    null,  r8,    null,  a,     null,  null,  null,  a8,    null,
	/* F0 */  a8_i,  null,  c_i,   null,  null,  af,    a8,    null,  r8,    hl,    a16_i, null,  null,  null,  a8,    null,
	};
	
	
	
	public static final String[] CB_INSTRUCTION_NAME = {
	// Extended opcodes, e.g. "CB 85" == "RES"
	/*        00     01     02     03     04     05     06     07     08     09     0A     0B     0C     0D     0E     0F */
	/* 00 */ "rlc", "rlc", "rlc", "rlc", "rlc", "rlc", "rlc", "rlc", "rrc", "rrc", "rrc", "rrc", "rrc", "rrc", "rrc", "rrc",
	/* 10 */ "rl",  "rl",  "rl",  "rl",  "rl",  "rl",  "rl",  "rl",  "rr",  "rr",  "rr",  "rr",  "rr",  "rr",  "rr",  "rr",
	/* 20 */ "sla", "sla", "sla", "sla", "sla", "sla", "sla", "sla", "sra", "sra", "sra", "sra", "sra", "sra", "sra", "sra",
	/* 30 */ "swap","swap","swap","swap","swap","swap","swap","swap","srl", "srl", "srl", "srl", "srl", "srl", "srl", "srl",
	/* 40 */ "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit",
	/* 50 */ "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit",
	/* 60 */ "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit",
	/* 70 */ "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit", "bit",
	/* 80 */ "RES", "RES", "RES", "RES", "RES", "RES", "RES", "RES", "res", "res", "res", "res", "res", "res", "res", "res",
	/* 90 */ "RES", "RES", "RES", "RES", "RES", "RES", "RES", "RES", "res", "res", "res", "res", "res", "res", "res", "res",
	/* A0 */ "RES", "RES", "RES", "RES", "RES", "RES", "RES", "RES", "res", "res", "res", "res", "res", "res", "res", "res",
	/* B0 */ "RES", "RES", "RES", "RES", "RES", "RES", "RES", "RES", "res", "res", "res", "res", "res", "res", "res", "res",
	/* C0 */ "SET", "SET", "SET", "SET", "SET", "SET", "SET", "SET", "set", "set", "set", "set", "set", "set", "set", "set",
	/* D0 */ "SET", "SET", "SET", "SET", "SET", "SET", "SET", "SET", "set", "set", "set", "set", "set", "set", "set", "set",
	/* E0 */ "SET", "SET", "SET", "SET", "SET", "SET", "SET", "SET", "set", "set", "set", "set", "set", "set", "set", "set",
	/* F0 */ "SET", "SET", "SET", "SET", "SET", "SET", "SET", "SET", "set", "set", "set", "set", "set", "set", "set", "set",
	};
	
	public static final int[] CB_INSTRUCTION_SIZE = {
	// Yes, literally every instruction here is 2 bytes, including the CB prefix and the extended opcode byte.
	/*        00     01     02     03     04     05     06     07     08     09     0A     0B     0C     0D     0E     0F */
	/* 00 */   2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
	/* 10 */   2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
	/* 20 */   2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
	/* 30 */   2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
	/* 40 */   2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
	/* 50 */   2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
	/* 60 */   2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
	/* 70 */   2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
	/* 80 */   2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
	/* 90 */   2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
	/* A0 */   2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
	/* B0 */   2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
	/* C0 */   2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
	/* D0 */   2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
	/* E0 */   2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
	/* F0 */   2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,     2,
	};
	
	public final SM83Opcode[] cbInstructionLogic = {
	/*        00     01     02     03     04     05     06     07     08     09     0A     0B     0C     0D     0E     0F */
	/* 00 */  RLC,   RLC,   RLC,   RLC,   RLC,   RLC,   RLC,   RRC,   RRC,   RRC,   RRC,   RRC,   RRC,   RRC,   RRC,   RRC,
	/* 10 */  RL,    RL,    RL,    RL,    RL,    RL,    RL,    RL,    RR,    RR,    RR,    RR,    RR,    RR,    RR,    RR,
	/* 20 */  SLA,   SLA,   SLA,   SLA,   SLA,   SLA,   SLA,   SLA,   SRA,   SRA,   SRA,   SRA,   SRA,   SRA,   SRA,   SRA,
	/* 30 */  SWAP,  SWAP,  SWAP,  SWAP,  SWAP,  SWAP,  SWAP,  SWAP,  SRL,   SRL,   SRL,   SRL,   SRL,   SRL,   SRL,   SRL,
	/* 40 */  BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,
	/* 50 */  BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,
	/* 60 */  BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,
	/* 70 */  BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,   BIT,
	/* 80 */  RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,
	/* 90 */  RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,
	/* A0 */  RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,
	/* B0 */  RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,   RES,
	/* C0 */  SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,
	/* D0 */  SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,
	/* E0 */  SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,
	/* F0 */  SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,   SET,
	};
	
	public final Operand[] cbInstructionDestOperand = {
	/*        00     01     02     03     04     05     06     07     08     09     0A     0B     0C     0D     0E     0F */
	/* 00 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* 10 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* 20 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* 30 */  c,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* 40 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* 50 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* 60 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* 70 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* 80 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* 90 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* A0 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* B0 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* C0 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* D0 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* E0 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* F0 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	};
	
	public final Operand[] cbInstructionSourceOperand = {
	/*        00     01     02     03     04     05     06     07     08     09     0A     0B     0C     0D     0E     0F */
	/* 00 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* 10 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* 20 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* 30 */  b,     c,     d,     e,     h,     l,     hl_i,  a,     b,     c,     d,     e,     h,     l,     hl_i,  a,
	/* 40 */  v0,    v0,    v0,    v0,    v0,    v0,    v0,    v0,    v1,    v1,    v1,    v1,    v1,    v1,    v1,    v1,
	/* 50 */  v2,    v2,    v2,    v2,    v2,    v2,    v2,    v2,    v3,    v3,    v3,    v3,    v3,    v3,    v3,    v3,
	/* 60 */  v4,    v4,    v4,    v4,    v4,    v4,    v4,    v4,    v5,    v5,    v5,    v5,    v5,    v5,    v5,    v5,
	/* 70 */  v6,    v6,    v6,    v6,    v6,    v6,    v6,    v6,    v7,    v7,    v7,    v7,    v7,    v7,    v7,    v7,
	/* 80 */  v0,    v0,    v0,    v0,    v0,    v0,    v0,    v0,    v1,    v1,    v1,    v1,    v1,    v1,    v1,    v1,
	/* 90 */  v2,    v2,    v2,    v2,    v2,    v2,    v2,    v2,    v3,    v3,    v3,    v3,    v3,    v3,    v3,    v3,
	/* A0 */  v4,    v4,    v4,    v4,    v4,    v4,    v4,    v4,    v5,    v5,    v5,    v5,    v5,    v5,    v5,    v5,
	/* B0 */  v6,    v6,    v6,    v6,    v6,    v6,    v6,    v6,    v7,    v7,    v7,    v7,    v7,    v7,    v7,    v7,
	/* C0 */  v0,    v0,    v0,    v0,    v0,    v0,    v0,    v0,    v1,    v1,    v1,    v1,    v1,    v1,    v1,    v1,
	/* D0 */  v2,    v2,    v2,    v2,    v2,    v2,    v2,    v2,    v3,    v3,    v3,    v3,    v3,    v3,    v3,    v3,
	/* E0 */  v4,    v4,    v4,    v4,    v4,    v4,    v4,    v4,    v5,    v5,    v5,    v5,    v5,    v5,    v5,    v5,
	/* F0 */  v6,    v6,    v6,    v6,    v6,    v6,    v6,    v6,    v7,    v7,    v7,    v7,    v7,    v7,    v7,    v7,
	};
	
	
}
