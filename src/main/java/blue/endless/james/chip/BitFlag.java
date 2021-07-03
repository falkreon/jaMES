package blue.endless.james.chip;

public class BitFlag {
	private final int bit;
	private final int mask;
	private final int cutout;
	
	public BitFlag(int bitNumber) {
		bit=bitNumber;
		this.mask = 1 << bitNumber;
		this.cutout = ~mask;
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
