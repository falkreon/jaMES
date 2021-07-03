package blue.endless.james.chip.mos6502;

import blue.endless.james.core.felines.NesCore;
import blue.endless.james.host.Bus;

public interface MemoryMode {
	/**
	 * Loads an internal prefetch register with an address.
	 * @param bus The bus the mapper is on
	 * @param regs The register file the Cpu is using
	 * @return the number of cycles added by this addressing mode
	 */
	public int prepareFetch(Bus bus, RegisterFile regs);
	
	
	public static final MemoryMode IMPLIED = (bus, regs)->{
		regs.setFetchLocation(0);
		//no memory fetch occurs here
		return 0;
	};
	
	public static final MemoryMode IMMEDIATE = (bus, regs)->{
		//read 1 byte after inst: strangely enough this extra read falls within the same clock as the instruction logic
		regs.setFetchLocation( regs.getPC() );
		regs.incPC();
		
		return 0;
	};
	
	public static final MemoryMode ZEROPAGE = (bus, regs)->{
		//read 1 byte after inst: 1
		long zpAddress = bus.read(regs.getPC()) & 0xFF;
		regs.incPC(); 
		
		regs.setFetchLocation(zpAddress);
		
		return 1;
	};
	
	public static final MemoryMode ZEROPAGE_X = (bus, regs)->{
		//read 1 byte after inst: 1
		long zpAddress = bus.read(regs.getPC()) & 0xFF; regs.incPC();
		
		//add & wrap: 1
		zpAddress += regs.getX();
		zpAddress &= 0xFF;
		regs.setFetchLocation(zpAddress);
		
		return 2;
	};
	
	//LDX and STX only
	public static final MemoryMode ZEROPAGE_Y = (bus, regs)->{
		//read zp address from instn: 1
		long zpAddress = bus.read(regs.getPC()) & 0xFF; regs.incPC();
		
		//add y: 1
		zpAddress += regs.getY();
		zpAddress &= 0xFF;
		
		regs.setFetchLocation(zpAddress);
		
		return 2;
	};
	
	public static MemoryMode ABSOLUTE = (bus, regs)->{
		//read absolute address from instn: 2
		long lowAddress = bus.read(regs.getPC()) & 0xFF; regs.incPC();
		long highAddress = bus.read(regs.getPC()) & 0xFF; regs.incPC();
		long absAddress = (highAddress << 8) | lowAddress;
		
		regs.setFetchLocation(absAddress);
		
		return 2;
	};
	
	public static MemoryMode ABSOLUTE_Y = (bus, regs)->{
		int extraCycle = 0;
		
		//read absolute address from instn: 2
		long lowAddress = bus.read(regs.getPC()) & 0xFF; regs.incPC();
		long highAddress = bus.read(regs.getPC()) & 0xFF; regs.incPC();
		long absAddress = (highAddress << 8) | lowAddress;
		
		//modify operand by adding y: 1
		absAddress += regs.getY();
		if (((absAddress >> 8) & 0xFF) != highAddress) extraCycle = 1;
		
		regs.setFetchLocation(absAddress & 0xFFFF);
		
		return 3 + extraCycle;
	};
	
	public static MemoryMode ABSOLUTE_X = (bus, regs)->{
		int extraCycle = 0;
		
		//read absolute address from instn: 2
		long lowAddress = bus.read(regs.getPC()) & 0xFF; regs.incPC();
		long highAddress = bus.read(regs.getPC()) & 0xFF; regs.incPC();
		long absAddress = (highAddress << 8) | lowAddress;
		
		//modify operand by adding y: 1
		absAddress += regs.getX();
		if (((absAddress >> 8) & 0xFF) != highAddress) extraCycle = 1;
		
		regs.setFetchLocation(absAddress & 0xFFFF);
		
		return 3 + extraCycle;
	};
	
	//JMP only
	public static final MemoryMode INDIRECT = (bus, regs)->{
		int lowAddress = bus.read(regs.getPC()) & 0xFF; regs.incPC();
		int highAddress = bus.read(regs.getPC()) & 0xFF; regs.incPC();
		long absAddress = (highAddress << 8) | lowAddress;
		
		if (lowAddress==0xFF) {
			/**
			 * This is a bug in 6502 hardware; normally, when an access crosses a page boundary,
			 * an extra cycle is taken to add the carry bit to the high byte to fixup the address.
			 * 
			 * In this case, the low byte is read from the correct address, but then the high byte
			 * is read from the very start of the page instead. You should probably not count on
			 * this behavior.
			 */
			int indLow = bus.read(absAddress) & 0xFF;
			int indHigh = bus.read(absAddress & 0xFF00) & 0xFF;
			int indAddress = (indHigh << 8) | indLow;
			
			regs.setFetchLocation(indAddress);
		} else {
			int indLow = bus.read(absAddress) & 0xFF;
			int indHigh = bus.read(absAddress+1) & 0xFF;
			int indAddress = (indHigh << 8) | indLow;
		
			regs.setFetchLocation(indAddress);
		}
		
		return 4; //two 16-bit fetches
	};
	
	public static final MemoryMode INDIRECT_X = (bus, regs)->{
		//read zp address from instn: 1
		long zpAddress = bus.read(regs.getPC()) & 0xFF; regs.incPC();
		
		//add x and wrap: 1
		zpAddress += regs.getX();
		
		//dereference: 2
		int indLow = bus.read(zpAddress) & 0xFF;
		int indHigh = bus.read((zpAddress+1) & 0xFF) & 0xFF; //I have no evidence that this zeropage-wrap exists here, but I bet it does.
		int indAddress = (indHigh << 8) | indLow;
		
		regs.setFetchLocation(indAddress);
		
		return 4;
	};
	
	public static final MemoryMode INDIRECT_Y = (bus, regs)->{
		long zpAddress = bus.read(regs.getPC()) & 0xFF; regs.incPC();
		
		//Dereference the zero-page addr from the instruction
		int indLow = bus.read(zpAddress) & 0xFF;
		int indHigh = bus.read((zpAddress+1) & 0xFF) & 0xFF; //I have no evidence that this zeropage-wrap exists here, but I bet it does.
		int indAddress = (indHigh << 8) | indLow;
		
		
		long absAddress = (indAddress + regs.getY()) & 0xFFFF;
		
		//add and wrap
		int extraCycle = (absAddress>>8 != indAddress>>8) ? 1 : 0; //we crossed a page boundary and need to fixup the high addr byte

		regs.setFetchLocation(absAddress);
		
		return 5+extraCycle; 
	};
	
	public static final MemoryMode RELATIVE = (bus, regs)->{
		long relativeAddress = bus.read(regs.getPC()) & 0xFF; regs.incPC();
		
		if ((relativeAddress & 0x80)!=0) relativeAddress |= ~0xFFL; //sign-extend from one byte to the full data type
		long branchTarget = regs.getPC() + relativeAddress;
		branchTarget &= 0xFFFF;
		
		regs.setFetchLocation(branchTarget);
		
		return 1; //all the extra-cycles magic happens in the instruction due to branch stalls
	};
}
