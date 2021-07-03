package blue.endless.james.chip.mos6502;

public class CallFrame {
	public String opcode;
	public long from;
	public long to;
	public long count;
	
	public CallFrame(String opcode, long from, long to) {
		this.opcode = opcode;
		this.from = from;
		this.to = to;
		this.count = 1;
	}
	
	public void inc() {
		this.count++;
	}
	
	public String toString() {
		return "{ op: "+opcode+", from: "+Integer.toHexString((int) from & 0xFFFF)+", to: "+Integer.toHexString((int) to & 0xFFFF)+", times: "+count+" }";
	}
}
