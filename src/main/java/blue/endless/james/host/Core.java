package blue.endless.james.host;

import blue.endless.tinyevents.impl.ConsumerEvent;

public interface Core {
	int getFrameWidth();
	int getFrameHeight();
	ConsumerEvent<int[]> onPresentFrame();
	void softReset();
	void hardReset();
	int clock();
	
	public boolean isStopped();
	public void setStopped(boolean stopped);
	
	public void connectControls(ControlSet controls);
	void connectBios(byte[] gbRom);
}
