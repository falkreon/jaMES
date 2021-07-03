package blue.endless.james.chip.z80;

import blue.endless.james.chip.BitFlag;

public class Z80RegisterFile {
	public static final BitFlag FLAG_CARRY        = new BitFlag(0);
	public static final BitFlag FLAG_ADD_SUBTRACT = new BitFlag(1);
	public static final BitFlag FLAG_OVERFLOW     = new BitFlag(2);
	public static final BitFlag FLAG_UNUSED_1     = new BitFlag(3);
	public static final BitFlag FLAG_ADJUST       = new BitFlag(4);
	public static final BitFlag FLAG_UNUSED_2     = new BitFlag(5);
	public static final BitFlag FLAG_ZERO         = new BitFlag(6);
	public static final BitFlag FLAG_SIGN         = new BitFlag(7);
	
	public int af = 0;
	public int bc = 0;
	public int de = 0;
	public int hl = 0;
	
	
	public int ix = 0;
	public int iy = 0;
	public int sp = 0;
	
	public int i = 0;
	public int r = 0;
	
	public int pc = 0;
	
	public int getA() {
		return (af >> 8) & 0xFF;
	}
	
	public boolean isSet(BitFlag flag) {
		return flag.isSet(af);
	}
	
	public void set(BitFlag flag) {
		af = flag.set(af);
	}
	
	public void set(BitFlag flag, boolean value) {
		af = flag.set(af, value);
	}
	
	public void set(BitFlag flag, int value) {
		af = flag.set(af, value);
	}
	
	public void clear(BitFlag flag) {
		af = flag.clear(af);
	}
}
