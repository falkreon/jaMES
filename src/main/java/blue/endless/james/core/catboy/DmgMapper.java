package blue.endless.james.core.catboy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import blue.endless.james.host.Bus;

public abstract class DmgMapper implements Bus {
	public static final int BANK_LENGTH    = 0x4000;
	public static final int BANK_TWO_START = BANK_LENGTH;
	public static final int BANK_TWO_END   = BANK_TWO_START + BANK_LENGTH;
	public static final int RAM_LENGTH     = 0x2000;
	public static final int RAM_START      = 0xA000;
	public static final int RAM_END        = RAM_START + RAM_LENGTH;
	
	public static List<byte[]> getBanks(byte[] romFile) {
		ArrayList<byte[]> result = new ArrayList<>();
		
		int pos=0;
		while(pos<romFile.length) {
			byte[] bank = new byte[BANK_LENGTH];
			Arrays.fill(bank, (byte) 0xFF); //In the DMG, typically unused space is reported as 0xFF
			
			for(int i=0; i<BANK_LENGTH; i++) {
				if (pos+i < romFile.length) bank[i] = romFile[pos+i];
			}
			result.add(bank);
			
			pos += BANK_LENGTH;
		}
		
		return result;
	}
	
	@Override
	public boolean mapsRead(long address) {
		if (address >= 0x0000 && address < 0x8000) return true;
		if (address >= 0xA000 && address < 0xC000) return true;
		return false;
	}
	
	@Override
	public boolean mapsWrite(long address) {
		if (address >= 0x0000 && address < 0x8000) return true;
		if (address >= 0xA000 && address < 0xC000) return true;
		return false;
	}
	
	public boolean isRamDirty() {
		return false;
	}
	
	public void clearRamDirty() {
		//no RAM, do nothing
	}
	
	public byte[] getSaveRam() {
		return null;
	}
	
	public static class None extends DmgMapper {
		private byte[] lowBank;
		private byte[] highBank;
		
		public None(List<byte[]> banks) {
			this.lowBank = banks.get(0);
			this.highBank = banks.get(1);
		}
		
		@Override
		public boolean mapsRead(long address) {
			if (address >= 0x0000 && address < 0x8000) return true;
			return false;
		}
		
		@Override
		public boolean mapsWrite(long address) {
			return false;
		}
		

		@Override
		public int read(long address) {
			if (address >= 0x0000 && address < BANK_TWO_START) {
				return lowBank[(int) address % BANK_LENGTH];
			} else if (address >= BANK_TWO_START && address < BANK_TWO_END) {
				int localAddress = (int) address - BANK_TWO_START;
				return highBank[localAddress % BANK_LENGTH];
			}
			
			return 0xFF;
		}

		@Override
		public void write(long address, int value) {
			System.out.println("ROM WRITE TO NONEXISTANT MemoryBankController: 0x"+Integer.toHexString(value)+" at (0x"+Integer.toHexString((int) address)+")");
		}
	}
	
	public static class Mbc1 extends DmgMapper {
		private List<byte[]> banks;
		private byte[] saveRam = new byte[RAM_LENGTH];
		private int selectedBank = 1;
		private boolean ramEnable;
		private boolean ramDirtyFlag = false;
		
		public Mbc1(List<byte[]> banks) {
			this.banks = banks;
			this.selectedBank = 1;
			Arrays.fill(saveRam, (byte) 0xFF);
		}

		@Override
		public int read(long address) {
			if (address >= 0x0000 && address < BANK_TWO_START) {
				return banks.get(0)[(int) address % BANK_LENGTH];
			} else if (address >= BANK_TWO_START && address < BANK_TWO_END) {
				int localAddress = (int) address - BANK_TWO_START;
				return banks.get(selectedBank)[localAddress % BANK_LENGTH];
			} else if (address >= RAM_START && address < RAM_END) {
				int localAddress = (int) address - RAM_START;
				return saveRam[localAddress % RAM_LENGTH];
			}
			
			return 0xFF;
		}

		@Override
		public void write(long address, int value) {
			if (address >= 0x0000 && address < 0x2000) {
				ramEnable = (value & 0x0F) == 0x0A;
				System.out.println((ramEnable) ? "RAM Enabled" : "RAM disabled");
			} else if (address >= 0x2000 && address < 0x4000) {
				value = value & 0b0011111; //lower 5 bits of bank number
				if (value==0) value=1; //Cannot select bank 0 (or 0x20, or 0x40, or 0x60)
				selectedBank = selectedBank & 0b1100000;
				selectedBank = selectedBank | value;
				//System.out.println("Swapped bank to "+selectedBank);
			} else if (address >= 0x4000 && address < 0x6000) {
				//TODO: RAM Bank number / ROM bank upper 2 bits
			} else if (address >= 0x6000 && address < 0x8000) {
				//TODO: ROM/RAM Mode Select
			} else if (address >= RAM_START && address < RAM_END) {
				if (!ramEnable) return;
				int localAddress = (int) address - RAM_START;
				saveRam[localAddress % RAM_LENGTH] = (byte) value;
				ramDirtyFlag = true;
			} else {
				System.out.println("ROM WRITE: 0x"+Integer.toHexString(value)+" at (0x"+Integer.toHexString((int) address)+")");
			}
		}
		
		@Override
		public boolean isRamDirty() {
			return ramDirtyFlag;
		}
		
		@Override
		public void clearRamDirty() {
			ramDirtyFlag = false;
		}
		
		@Override
		public byte[] getSaveRam() {
			return saveRam;
		}
	}
	
	public static class Mbc5 extends DmgMapper {
		private List<byte[]> banks;
		private byte[] saveRam = new byte[RAM_LENGTH];
		private int selectedBank = 1;
		private int ramBank = 0;
		private boolean ramEnable;
		private boolean ramDirtyFlag = false;
		
		public Mbc5(List<byte[]> banks) {
			this.banks = banks;
			this.selectedBank = 1;
			Arrays.fill(saveRam, (byte) 0xFF);
		}

		@Override
		public int read(long address) {
			if (address >= 0x0000 && address < BANK_TWO_START) {
				return banks.get(0)[(int) address % BANK_LENGTH];
			} else if (address >= BANK_TWO_START && address < BANK_TWO_END) {
				int localAddress = (int) address - BANK_TWO_START;
				return banks.get(selectedBank)[localAddress % BANK_LENGTH];
			} else if (address >= RAM_START && address < RAM_END) {
				int localAddress = (int) address - RAM_START;
				return saveRam[localAddress % RAM_LENGTH];
			}
			
			return 0xFF;
		}

		@Override
		public void write(long address, int value) {
			if (address >= 0x0000 && address < 0x2000) {
				ramEnable = (value & 0x0F) == 0x0A;
				System.out.println((ramEnable) ? "RAM Enabled" : "RAM disabled");
			} else if (address >= 0x2000 && address < 0x3000) {
				value = value & 0xFF; //lower 8 bits of bank number
				selectedBank = selectedBank & 0xFF00;
				selectedBank = selectedBank | value;
				//System.out.println("Swapped bank to "+selectedBank);
			} else if (address >= 0x3000 && address < 0x4000) {
				value = value & 0x01;
				selectedBank = selectedBank & 0xFF;
				selectedBank = selectedBank | value;
				//System.out.println("Swapped bank to "+selectedBank);
			} else if (address >= 0x4000 && address < 0x6000) {
				ramBank = value & 0x0F;
				//System.out.println("RAM Bank is now "+ramBank);
			} else if (address >= RAM_START && address < RAM_END) {
				if (!ramEnable) return;
				int localAddress = (int) address - RAM_START;
				saveRam[localAddress % RAM_LENGTH] = (byte) value;
				ramDirtyFlag = true;
			} else {
				System.out.println("ROM WRITE: 0x"+Integer.toHexString(value)+" at (0x"+Integer.toHexString((int) address)+")");
			}
		}
		
		@Override
		public boolean isRamDirty() {
			return ramDirtyFlag;
		}
		
		@Override
		public void clearRamDirty() {
			ramDirtyFlag = false;
		}
		
		@Override
		public byte[] getSaveRam() {
			return saveRam;
		}
	}
}
