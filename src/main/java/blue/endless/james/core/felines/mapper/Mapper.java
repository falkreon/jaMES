package blue.endless.james.core.felines.mapper;

public interface Mapper {
	
	/**
	 * Reads a byte from memory, from the perspective of the Cpu.
	 * @param addr the address of the byte to read
	 * @param value the byte from unmapped ram at this address
	 * @return the actual value of reading at this address. May cause side effects.
	 */
	public int cpuRead(long addr, int value);
	
	/**
	 * Writes a byte to memory, from the perspective of the Cpu.
	 * @param addr the address of the memory to be written to.
	 * @param value
	 * @return true if the mapper claims responsibility for this address
	 */
	public boolean cpuWrite(long addr, int value);
	
	/**
	 * Reads a byte from memory, from the perspective of the Ppu
	 * @param addr the address of the byte to read
	 * @param vram the host system's pre-mapped (but mirrored) answer
	 * @return vram if the mapper passes the value through unchanged, or a different answer if this location is mapped
	 */
	public int ppuRead(long addr, int vram);
	public long ppuWrite(long addr);
	
	/**
	 * Takes a logical address on the PPU memory bus in the nametable space ($2000-$2FFF) and maps it
	 * to a *physical* address into the 2KiB ppu memory
	 * @param addr A logical address between 0x2000 and 0x2FFF, inclusive
	 * @return A physical address between 0x000 and 0x07FF, inclusive
	 */
	public long ppuMirror(long addr);
	
	//public int physicalRead(long addr);
	//public int physicalWrite(long addr, int val);
}
