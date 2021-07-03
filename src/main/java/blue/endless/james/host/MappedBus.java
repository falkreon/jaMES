package blue.endless.james.host;

import java.util.ArrayList;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class MappedBus implements Bus {
	private ArrayList<ArrayMapping> priorityRoms = new ArrayList<>();
	private ArrayList<ReadMapping> readMappings = new ArrayList<>();
	private ArrayList<WriteMapping> writeMappings = new ArrayList<>();
	private ArrayList<Bus> childBuses = new ArrayList<>();
	private int unmappedValue = 0;
	
	@Override
	public int read(long addr) {
		for(ArrayMapping mapping : priorityRoms) {
			if (addr >= mapping.getStart() && addr < mapping.getStart() + mapping.getSize()) {
				return mapping.read(addr - mapping.getStart());
			}
		}
		
		for(Bus mapper : childBuses) {
			if (mapper.mapsRead(addr)) return mapper.read(addr);
		}
		
		for(ReadMapping mapping : readMappings) {
			if (addr >= mapping.getStart() && addr < mapping.getStart() + mapping.getSize()) {
				return mapping.read(addr - mapping.getStart());
			}
		}
		
		return unmappedValue;
	}

	@Override
	public void write(long addr, int val) {
		for(Bus mapper : childBuses) {
			if (mapper.mapsWrite(addr)) {
				mapper.write(addr, val);
				return;
			}
		}
		
		for(WriteMapping mapping : writeMappings) {
			if (addr >= mapping.getStart() && addr < mapping.getStart() + mapping.getSize()) {
				mapping.write(addr - mapping.getStart(), val);
				return;
			}
		}
	}
	
	public void setUnmappedValue(int value) {
		this.unmappedValue = value;
	}
	
	public void map(IntSupplier supplier, int addr) {
		readMappings.add(new SupplierMapping(supplier, addr));
	}
	
	public void map(IntConsumer consumer, int addr) {
		writeMappings.add(new ConsumerMapping(consumer, addr));
	}
	
	public void map(IntSupplier supplier, IntConsumer consumer, int addr) {
		map(supplier, addr);
		map(consumer, addr);
	}
	
	public void map(byte[] source, int start) {
		map(source, start, source.length);
	}
	
	public void map(byte[] source, int start, int size) {
		ArrayMapping mapping = new ArrayMapping();
		mapping.source = source;
		mapping.start = start;
		mapping.size = size;
		readMappings.add(mapping);
		writeMappings.add(mapping);
	}
	
	public <T extends ReadMapping & WriteMapping> void map(T mapper) {
		readMappings.add(mapper);
		writeMappings.add(mapper);
	}
	
	public void map(Bus mapper) {
		childBuses.add(mapper);
	}
	
	public void priorityMap(byte[] source, int start) {
		ArrayMapping mapping = new ArrayMapping();
		mapping.source = source;
		mapping.start = start;
		mapping.size = source.length;
		priorityRoms.add(mapping);
	}
	
	public void unmap(byte[] source) {
		for(int i=0; i<priorityRoms.size(); i++) {
			ArrayMapping mapping = priorityRoms.get(i);
			if (mapping.source==source) {
				priorityRoms.remove(i);
			}
		}
		
		for(int i=0; i<readMappings.size(); i++) {
			ReadMapping mapping = readMappings.get(i);
			if (mapping instanceof ArrayMapping && ((ArrayMapping)mapping).source==source) {
				readMappings.remove(i);
				break;
			}
		}
		
		for(int i=0; i<writeMappings.size(); i++) {
			WriteMapping mapping = writeMappings.get(i);
			if (mapping instanceof ArrayMapping && ((ArrayMapping)mapping).source==source) {
				writeMappings.remove(i);
				break;
			}
		}
	}
	
	public <T extends ReadMapping & WriteMapping> void unmap(T mapper) {
		for(int i=0; i<readMappings.size(); i++) {
			ReadMapping mapping = readMappings.get(i);
			if (mapping == mapper) {
				readMappings.remove(i);
				break;
			}
		}
		
		for(int i=0; i<writeMappings.size(); i++) {
			WriteMapping mapping = writeMappings.get(i);
			if (mapping == mapper) {
				writeMappings.remove(i);
				break;
			}
		}
	}
	
	public void unmap(Bus mapper) {
		childBuses.remove(mapper);
	}
	
	public void unmapAllMappers() {
		childBuses.clear();
	}
	
	public static interface ReadMapping {
		public long getStart();
		public long getSize();
		public int read(long relativeAddress);
	}
	
	public static interface WriteMapping {
		public long getStart();
		public long getSize();
		public void write(long relativeAddress, int value);
	}
	
	public static class SupplierMapping implements ReadMapping {
		public int start;
		public IntSupplier delegate;
		
		public SupplierMapping(IntSupplier supplier, int address) {
			this.delegate = supplier;
			this.start = address;
		}
		
		@Override
		public long getStart() {
			return start;
		}

		@Override
		public long getSize() {
			return 1;
		}

		@Override
		public int read(long relativeAddress) {
			return delegate.getAsInt() & 0xFF;
		}
	}
	
	public static class ConsumerMapping implements WriteMapping {
		public int start;
		public IntConsumer delegate;
		
		public ConsumerMapping(IntConsumer consumer, int address) {
			this.delegate = consumer;
			this.start = address;
		}
		
		@Override
		public long getStart() {
			return start;
		}
		
		@Override
		public long getSize() {
			return 1;
		}
		
		@Override
		public void write(long relativeAddress, int value) {
			delegate.accept(value & 0xFF);
		}
		
		
	}
	
	public static class ArrayMapping implements ReadMapping, WriteMapping {
		public int start;
		public int size;
		public byte[] source;
		
		@Override
		public long getStart() {
			return start;
		}
		
		@Override
		public long getSize() {
			return size;
		}
		
		@Override
		public void write(long relativeAddress, int value) {
			int localAddress = (int) (relativeAddress % source.length);
			source[localAddress] = (byte) value;
			
		}
		@Override
		public int read(long relativeAddress) {
			int localAddress = (int) (relativeAddress % source.length); 
			return source[localAddress] & 0xFF;
		}
	}

	@Override
	public boolean mapsRead(long address) {
		return true;
	}

	@Override
	public boolean mapsWrite(long address) {
		return true;
	}
}
