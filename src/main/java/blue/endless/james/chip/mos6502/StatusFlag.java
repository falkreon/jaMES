package blue.endless.james.chip.mos6502;

public enum StatusFlag {
	CARRY(0),
	ZERO(1),
	INTERRUPT_DISABLE(2),
	DECIMAL(3),
	B(4),
	OVERFLOW(6),
	NEGATIVE(7),
	
	/** NOT PRESENT IN HARDWARE, this flag is set when the machine is disconnected from the clock signal and will no longer run. */
	STOPPED(8);
	
	private final int bit;
	private final int mask;
	private final int cutout;
	
	StatusFlag(int bitNumber) {
		bit=bitNumber;
		if (bitNumber==4) {
			//Special logic for B because it's two bits
			this.mask   = 0b0011_0000;
			this.cutout = ~mask;
		} else {
			this.mask = 1 << bitNumber;
			this.cutout = ~mask;
		}
	}
	
	public int intValue(int val) {
		return (val&mask) >> bit;
	}
	
	public boolean isSet(int val) {
		return (val&mask)!=0;
	}
	
	public int set(int val) {
		return val|mask;
	}
	
	public int clear(int val) {
		return val&cutout;
	}
	
	public int set(int val, boolean set) {
		return (val&cutout) | ((set)?mask:0);
	}
	
	public int set(int val, int bit) {
		return (val&cutout) | ((bit&1)<<this.bit);
	}
}
