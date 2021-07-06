package blue.endless.james.host;

import blue.endless.tinyevents.impl.ConsumerEvent;

/**
 * Cores are the host's interface to the "emulated hardware" part of the emulator. A core typically represents a
 * specific emulated system such as "the NES", or an etremely closely-related group of hardware such as "the DMG, CGB,
 * and SGB".
 */
public interface Core {
	/**
	 * Gets the video refresh rate of the emulated system, in Hz. Typically 60.0, but can vary from system to system,
	 * especially PAL devices.
	 */
	public double getRefreshRate();
	/**
	 * Gets the primary CPU clock rate of the system, in Hz. That is, how many of the units returned by {@link #clock()}
	 * should occur per second.
	 */
	public double getClockSpeed();
	
	/**
	 * Gets the width of the last frame presented via the onPresentFrame() method.
	 * @return
	 */
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
