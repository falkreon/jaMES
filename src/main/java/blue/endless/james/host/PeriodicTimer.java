package blue.endless.james.host;

public class PeriodicTimer {
	/**
	 * If one frame overshoots its time, the next frame may be allowed to undershoot its
	 * time to a certain extent. Likewise, if one frame undershoots its timeslot, the
	 * next frame may be allowed to overshoot it. Allowing a little jitter may help the
	 * system maintain a stable rate, but allowing too much can cause the system to
	 * fishtail. In this case, we allow a frame to "borrow" or "lend" up to 500msec from/to
	 * the next frame.
	 */
	private static final long MAX_REMAINDER = 5L;

	private long previous;
	private long period = (long) ((1.0 / 60.0) * 1000.0); // 1/60th of a second in msec
	private long remainder = 0L;
	private long maxRemainder = MAX_REMAINDER;
	private long lastTimerError = 0L;
	private long maxTimerError = 0L;
	private boolean spinlock = false;
	private long errorSamples = 1L;
	private long curErrorSamples = 0L;

	public PeriodicTimer() {
		previous = now();
	}

	public PeriodicTimer(long period) {
		previous = now();
		this.period = period;
	}

	public void setPeriodMillis(long period) {
		this.period = period;
	}

	public void setPeriodHertz(double hertz) {
		double period = (1.0/hertz) * 1000.0;
		this.period = (long) period;
		this.errorSamples = (long) (hertz / 2.0);
		this.curErrorSamples = 0L;
	}

	public void waitForPeriod() {
		long now = now();
		long elapsed = now-previous;
		long frameDuration = period-remainder;

		if (elapsed >= frameDuration) {
			latch(now, elapsed - frameDuration); //positive millis will borrow time from the following frame
			return;
		} else {
			sleepLock(now, frameDuration - lastTimerError);
			spinLock(now(), frameDuration);
		}

		now = now();
		elapsed = now-previous;
		long remainder = elapsed - frameDuration; //if we fell short, elapsed will be less than frameDuration and we'll lend the next frame some millis.
		latch(now, remainder);
	}

	/**
	 * Do Nothing until our desired timeslice arrives, relinquishing thread control in the meantime. If this
	 * thread is interrupted and control is regained before the desired time, this method will put the thread
	 * back to sleep as many times as necessary until the desired time arrives.
	 * @param now           the most recently measured timestamp
	 * @param frameDuration the amount of time to ensure has elapsed since {@code previous}
	 */
	private void sleepLock(long now, long frameDuration) {
		long elapsed = now - previous;

		while (elapsed < frameDuration) {
			long sleepDuration = frameDuration - elapsed;
			sleepDuration -= lastTimerError;

			if (sleepDuration > 0) {
				try {
					Thread.sleep(sleepDuration);
				} catch (InterruptedException e) {
					//
				}
			} else {
				break;
			}

			long nextElapsed = now() - previous;
			//TODO: Measure timer error
			long actualDuration = nextElapsed - elapsed; //how long did we sleep

			if (actualDuration > sleepDuration) {
				maxTimerError = Math.max(maxTimerError, actualDuration - sleepDuration);
			}

			elapsed = nextElapsed;
		}
	}

	/**
	 * Do Nothing until our desired timeslice arrives, but do not relinquish control of the thread. This is an
	 * inefficient but extremely precise method of timing, so do not do this unless we're too close to reliably
	 * hit the slice with sleepLock.
	 * @param now           the most recently measured timestamp
	 * @param frameDuration the amount of time to ensure has elapsed since {@code previous}
	 */
	private void spinLock(long now, long frameDuration) {
		if (!spinlock) return;
		long elapsed = now - previous;

		while (elapsed < frameDuration) {
			elapsed = now() - previous;
			//TODO: Perform some work?
		}
	}

	/**
	 * Latches the timer at the end of a frame so that previous becomes now, and sets the remainder.
	 * @param now       the most recently measured timestamp
	 * @param remainder the remainder time. Negative remainders will increase the available milliseconds for the next frame. Positive remainders will borrow time from the next frame.
	 */
	private void latch(long now, long remainder) {
		this.previous = now;
		this.remainder = Math.min(remainder, maxRemainder);

		curErrorSamples++;

		if (curErrorSamples > errorSamples) {
			lastTimerError = maxTimerError;
			maxTimerError = 0L;
			curErrorSamples = 0L;
		}
	}

	public long getLastTimestamp() {
		return previous;
	}

	/**
	 * Setting spinlock to {@code true} will cause the PeriodicTimer to loop actively waiting for a time-slice to end if it
	 * decides it can't reliably sleep for such a short duration.
	 */
	public void setSpinlock(boolean spinlock) {
		this.spinlock = spinlock;
	}

	public static long now() {
		return System.nanoTime() / 1_000_000L;
	}

	public static PeriodicTimer forFPS(double fps) {
		double period = (1.0 / fps) * 1000.0;
		PeriodicTimer result = new PeriodicTimer((long) period);
		result.errorSamples = (long) (fps / 2.0); //After fps/2 frames (i.e. half a second), lastTimerError will update to maxTimerError, and maxTimerError will reset to zero
		return result;
	}
}
