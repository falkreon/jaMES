package blue.endless.james.host;

public interface DebugView<T> {
	public void attach(T core);
	public void detatch();
}
