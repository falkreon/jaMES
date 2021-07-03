package blue.endless.james.host;

public interface Operand {
	public int load(int[] instruction);
	public void store(int[] instruction, int value);
	public default String trace(int[] instruction) {
		return "";
	}
	/** Perform any postfix operation, such as incrementing or decrementing a counter or value */
	public default void postfix() {};
}
