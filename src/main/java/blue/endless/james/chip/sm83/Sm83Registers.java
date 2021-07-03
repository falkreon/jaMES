package blue.endless.james.chip.sm83;

import blue.endless.james.chip.BitFlag;
import blue.endless.james.host.Operand;

public class Sm83Registers {
	public static final BitFlag FLAG_CARRY        = new BitFlag(4);
	public static final BitFlag FLAG_HALF_CARRY   = new BitFlag(5);
	public static final BitFlag FLAG_SUBTRACT     = new BitFlag(6);
	public static final BitFlag FLAG_ZERO         = new BitFlag(7);
	
	public int af = 0;
	public int bc = 0;
	public int de = 0;
	public int hl = 0;
	
	public int sp = 0;
	public int pc = 0;
	
	public int flags = 0;
	
	//INTERNAL EMULATOR REGS
	public boolean stopped = false;
	public int instructionAddress = 0;
	public boolean enableInterrupts = true;
	
	public int getA() {
		return (af >> 8) & 0xFF;
	}
	
	public void setA(int value) {
		value &= 0xFF;
		af &= 0xFF;
		af = af | (value << 8);
	}
	
	public void set(BitFlag flag) {
		af = flag.set(af);
	}
	
	public void clear(BitFlag flag) {
		af = flag.clear(af);
	}
	
	public void set(BitFlag flag, boolean val) {
		af = flag.set(af, val);
	}
	
	public void set(BitFlag flag, int val) {
		af = flag.set(af, val);
	}
	
	public boolean isSet(BitFlag flag) {
		return flag.isSet(af & 0x00FF);
	}
	
	public void affectZ(int value) {
		af = FLAG_ZERO.set(af, (value & 0xFF) == 0);
	}
	
	public void affectH(int a, int b) {
		boolean halfCarry = (((a & 0xf) + (b & 0xf)) & 0x10) == 0x10;
		
		af = FLAG_HALF_CARRY.set(af, halfCarry);
	}
	
	public void affectH16(int a, int b) {
		boolean halfCarry = (((a & 0xfff) + (b & 0xfff)) & 0x1000) != 0;
		
		af = FLAG_HALF_CARRY.set(af, halfCarry);
	}
	
	/** During an add-with-carry or a subtract-with-borrow, two operations are performed. This checks for half-carry on both of them. */
	public void affectH(int a, int b, int c) {
		boolean halfCarry = (((a & 0xf) + (b & 0xf)) & 0x10) == 0x10;
		int ab = (a+b) & 0xFF;
		boolean carryInHalfCarry = (((ab & 0xf) + (c & 0xf)) & 0x10) == 0x10;
		
		af = FLAG_HALF_CARRY.set(af, halfCarry | carryInHalfCarry);
	}
	
	public void affectC16(int value) {
		af = FLAG_CARRY.set(af, (value & ~0xFFFF) != 0);
	}
	
	public void affectC(int value) {
		af = FLAG_CARRY.set(af, (value & ~0xFF) != 0);
	}
	
	public Operand aOperand = new Operand() {
		@Override
		public int load(int[] instruction) {
			return getA();
		}

		@Override
		public void store(int[] instruction, int value) {
			setA(value);
		}
		
		public String trace(int[] instruction) {
			return "a";
		};
	};
	
	public Operand fOperand = new Operand() {

		@Override
		public int load(int[] instruction) {
			return af & 0xF0;
		}

		@Override
		public void store(int[] instruction, int value) {
			value &= 0xF0;
			af &= 0xFF00;
			af |= value;
		}
		
		public String trace(int[] instruction) {
			return "f";
		};
	};
	
	public Operand bOperand = new Operand() {
		@Override
		public int load(int[] instruction) {
			return (bc >> 8) & 0xFF;
		}

		@Override
		public void store(int[] instruction, int value) {
			value &= 0xFF;
			bc &= 0xFF;
			bc |= value << 8;
		}
		
		public String trace(int[] instruction) {
			return "b";
		};
	};
	
	public Operand cOperand = new Operand() {
		@Override
		public int load(int[] instruction) {
			return bc & 0xFF;
		}

		@Override
		public void store(int[] instruction, int value) {
			value = value & 0xFF;
			bc = bc & 0xFF00;
			bc = bc | value;
		}
		
		public String trace(int[] instruction) {
			return "c";
		};
	};
	
	public Operand dOperand = new Operand() {
		@Override
		public int load(int[] instruction) {
			return (de >> 8) & 0xFF;
		}

		@Override
		public void store(int[] instruction, int value) {
			value &= 0xFF;
			de &= 0xFF;
			de |= value << 8;
		}
		
		public String trace(int[] instruction) {
			return "d";
		};
	};
	
	public Operand eOperand = new Operand() {
		@Override
		public int load(int[] instruction) {
			return de & 0xFF;
		}

		@Override
		public void store(int[] instruction, int value) {
			value &= 0xFF;
			de &= 0xFF00;
			de |= value;
		}
		
		public String trace(int[] instruction) {
			return "e";
		};
	};
	
	public Operand hOperand = new Operand() {
		@Override
		public int load(int[] instruction) {
			return (hl >> 8) & 0xFF;
		}

		@Override
		public void store(int[] instruction, int value) {
			value &= 0xFF;
			hl &= 0xFF;
			hl |= value << 8;
		}
		
		public String trace(int[] instruction) {
			return "h";
		};
	};
	
	public Operand lOperand = new Operand() {
		@Override
		public int load(int[] instruction) {
			return hl & 0xFF;
		}

		@Override
		public void store(int[] instruction, int value) {
			value &= 0xFF;
			hl &= 0xFF00;
			hl |= value;
		}
		
		public String trace(int[] instruction) {
			return "l";
		};
	};
	
	public Operand afOperand = new Operand() {
		@Override
		public int load(int[] instruction) {
			return af & 0xFFF0;
		}

		@Override
		public void store(int[] instruction, int value) {
			af = value & 0xFFF0;
		}
		
		public String trace(int[] instruction) {
			return "af";
		};
	};
	
	public Operand bcOperand = new Operand() {
		@Override
		public int load(int[] instruction) {
			return bc & 0xFFFF;
		}

		@Override
		public void store(int[] instruction, int value) {
			bc = value & 0xFFFF;
		}
		
		public String trace(int[] instruction) {
			return "bc";
		};
	};
	
	public Operand deOperand = new Operand() {
		@Override
		public int load(int[] instruction) {
			return de & 0xFFFF;
		}

		@Override
		public void store(int[] instruction, int value) {
			de = value & 0xFFFF;
		}
		
		public String trace(int[] instruction) {
			return "de";
		};
	};
	
	public Operand hlOperand = new Operand() {
		@Override
		public int load(int[] instruction) {
			return hl & 0xFFFF;
		}

		@Override
		public void store(int[] instruction, int value) {
			hl = value & 0xFFFF;
		}
		
		public String trace(int[] instruction) {
			return "hl";
		};
	};
	
	public Operand spOperand = new Operand() {
		@Override
		public int load(int[] instruction) {
			return sp & 0xFFFF;
		}

		@Override
		public void store(int[] instruction, int value) {
			sp = value & 0xFFFF;
		}
		
		public String trace(int[] instruction) {
			return "sp";
		};
	};
	
	public String flagString() {
		String result = "";
		result += (isSet(FLAG_ZERO)) ? 'Z' : 'z';
		result += (isSet(FLAG_SUBTRACT)) ? 'N' : 'n';
		result += (isSet(FLAG_HALF_CARRY)) ? 'H' : 'h';
		result += (isSet(FLAG_CARRY)) ? 'C' : 'c';
		
		return result;
	}
}
