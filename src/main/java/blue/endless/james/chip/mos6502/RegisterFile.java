package blue.endless.james.chip.mos6502;

import java.util.ArrayDeque;

public class RegisterFile {
	
	private byte a;
	private byte x;
	private byte y;
	
	/** Program Counter */
	private long pc;
	
	/** Status register */
	private int p;
	
	/** Stack Pointer */
	private long probableStackTop = 0xFDL;
	private int s;
	
	private ArrayDeque<CallFrame> callStack = new ArrayDeque<>();
	
	/* Internal status, likely to be latches or analog behavior in the original hardware */
	
	/** Latched address to be fetched by instruction logic */
	private long instructionAddress;
	private long fetchLocation;
	private int debugLines = 0;
	//private boolean controllerStrobe = false;
	//private int controllerShift = 0;
	
	/** Throw a latch which causes the CPU to stop advancing the program counter and evaluating instructions. */
	public void hang() {
		set(StatusFlag.STOPPED);
	}
	
	public int getA() {
		return a & 0xFF;
	}
	
	public int getX() {
		return x & 0xFF;
	}
	
	public int getY() {
		return y & 0xFF;
	}
	
	public long getPC() {
		return this.pc;
	}
	
	public int getP() {
		return p & 0xFF;
	}
	
	public int getS() {
		return s & 0xFF;
	}
	
	/** Set A, updating zero and negative flags */
	public void setA(int val) {
		this.a = (byte) affectZN(val & 0xFF);
	}
	
	/** Set X, updating zero and negative flags */
	public void setX(int val) {
		this.x = (byte) affectZN(val & 0xFF);
	}
	
	/** Set Y, updating zero and negative flags */
	public void setY(int val) {
		this.y = (byte) affectZN(val & 0xFF);
	}
	
	/** Update the program counter to a new value */
	public void setPC(long val) {
		//System.out.println("Jump -> "+Integer.toHexString((int) val));
		this.pc = val;
	}
	
	/** Set the status byte. Note that this cannot affect the B register, which doesn't really exist */
	public void setP(int val) {
		this.p = StatusFlag.B.clear(val);
	}
	
	public void setS(int val) {
		this.s = (byte) val;
	}
	
	public void incPC() {
		pc++;
	}
	
	public void incPC(int offset) {
		pc += offset;
	}
	
	public void offsetStackPointer(int offset) {
		this.s = (this.s + offset) & 0xFF;
	}
	
	public void setFetchLocation(long val) {
		fetchLocation = val & 0xFFFFL;
	}
	
	public long getFetchLocation() {
		return fetchLocation & 0xFFFFL;
	}
	
	public void set(StatusFlag flag) {
		p = flag.set(p);
	}
	
	public void set(StatusFlag flag, boolean value) {
		p = flag.set(p, value);
	}
	
	public boolean isSet(StatusFlag flag) {
		return flag.isSet(p);
	}
	
	public void clear(StatusFlag flag) {
		p = flag.clear(p);
	}
	
	/** Set Zero and Negative Flags */
	public int affectZN(int val) {
		p = StatusFlag.ZERO.set(p, (val&0xFF)==0);
		p = StatusFlag.NEGATIVE.set(p, (val & 0x80) != 0);
		return val & 0xFF;
	}
	
	/** Set Zero, Negative, and Carry flags as needed by the value, and then wrap the value around so it fits in a byte */
	public int affectCZN(int val) {
		p = StatusFlag.CARRY.set(p, val > 0xFF);
		p = StatusFlag.ZERO.set(p, val==0);
		p = StatusFlag.NEGATIVE.set(p, val & 0x80);
		
		return val & 0xFF;
	}
	
	public void setInstructionAddress() {
		instructionAddress = pc & 0xFFFF;
	}
	
	public long getInstructionAddress() {
		return instructionAddress;
	}
	
	public void pushCallStack(String op, long from, long to) {
		//CallFrame top = callStack.peek();
		//if (top!=null && top.opcode.equals("JSR") && op.equals("RET")) {
			//Annihilate each other
		//	callStack.pop();
		//	return;
		//}
		//if (top!=null && top.from==from && top.to==to) {
		//	top.count++;
		//} else {
			CallFrame cur = new CallFrame(op, from, to);
			callStack.push(cur);
		//}
	}
	
	/*
	public void pushCallStack(long addr) {
		System.out.println("Jumping FROM "+Integer.toHexString((int)addr & 0xFFFF));
		callStack.push(addr);
	}
	
	public long popCallStack() {
		if (callStack.isEmpty()) {
			System.out.println("Unmatched pop at "+Integer.toHexString((int) pc & 0xFFFF));
			this.set(StatusFlag.STOPPED);
			return 0;
		} else {
			return callStack.pop();
		}
	}*/
	
	public int getRemainingDebugLines() {
		return debugLines;
	}
	
	public void decRemainingDebugLines() {
		debugLines--; if (debugLines<0) debugLines=0;
	}
	
	public void addDebugLines(int lines) {
		debugLines+= lines;
	}
	
	public CallFrame[] stackTrace() {
		return callStack.toArray(new CallFrame[callStack.size()]);
	}
	
	public void indicateProbableStackTop() {
		probableStackTop = s & 0xFF;
	}
	
	public long getProbableStackTop() {
		return probableStackTop;
	}
	
	@Override
	public String toString() {
		return "{ a: "+Integer.toHexString(a & 0xFF)+", x: "+Integer.toHexString(x & 0xFF)+", y: "+Integer.toHexString(y & 0xFF)+" p: "+Integer.toHexString(p & 0xFF)+" }";
	}
	
}
