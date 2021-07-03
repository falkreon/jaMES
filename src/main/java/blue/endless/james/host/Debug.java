package blue.endless.james.host;

public class Debug {
	public static String hexByte(int value) {
		String result = Integer.toHexString(value & 0xFF);
		while(result.length()<2) result = "0"+result;
		return result;
	}
	
	public static String hexBytes(int... values) {
		StringBuilder result = new StringBuilder();
		for(int i=0; i<values.length; i++) {
			if (i!=0) result.append(' ');
			result.append(hexByte(values[i] & 0xFF));
		}
		return result.toString();
	}
	
	@Deprecated
	public static String hexBytes(Bus bus, int address, int length) {
		StringBuilder result = new StringBuilder();
		for(int i=0; i<length; i++) {
			if (i!=0) result.append(' ');
			int val = bus.read(address+i) & 0xFF;
			result.append(hexByte(val));
		}
		return result.toString();
	}
	
	public static String hexBytes(byte[] b, int length) {
		StringBuilder result = new StringBuilder();
		for(int i=0; i<length; i++) {
			if (i!=0) result.append(' ');
			int val = b[i];
			result.append(hexByte(val));
		}
		return result.toString();
	}
	
	public static String hexBytes(int[] b, int length) {
		StringBuilder result = new StringBuilder();
		for(int i=0; i<length; i++) {
			if (i!=0) result.append(' ');
			int val = b[i];
			result.append(hexByte(val));
		}
		return result.toString();
	}
	
	public static String hexShort(int value) {
		String result = Integer.toHexString(value & 0xFFFF);
		while(result.length()<4) result = "0"+result;
		return result;
	}
	
	public static String padRight(String s, int length, char pad) {
		while(s.length()<length) s = s + pad;
		return s;
	}
}
