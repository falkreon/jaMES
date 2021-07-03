package blue.endless.james.host;

public interface Bus {
	public boolean mapsRead(long address);
	public boolean mapsWrite(long address);
	
	public int read(long address);
	public void write(long address, int value);
}
