package blue.endless.james.host;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;

public class Display {
	public static final int SPACE = KeyEvent.VK_SPACE;
	public static final int ENTER = KeyEvent.VK_ENTER;
	public static final int UP = KeyEvent.VK_UP;
	public static final int DOWN = KeyEvent.VK_DOWN;
	
	private JFrame frame = new JFrame();
	private int[] rawFrame;
	private BufferedImage curFrame;
	//private boolean[] scanCodes = new boolean[256];
	private ControlSet controls = new ControlSet();
	
	public Display() {
		frame.setTitle("Felines");
		frame.setIgnoreRepaint(true);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(640, 640);
		
		controls.bind(KeyEvent.VK_TAB, "Select");
		controls.bind(KeyEvent.VK_ENTER, "Start");
		controls.bind(KeyEvent.VK_Z, "A");
		controls.bind(KeyEvent.VK_A, "A");
		controls.bind(KeyEvent.VK_X, "B");
		controls.bind(KeyEvent.VK_S, "B");
		controls.bind(KeyEvent.VK_UP, "Up");
		controls.bind(KeyEvent.VK_DOWN, "Down");
		controls.bind(KeyEvent.VK_LEFT, "Left");
		controls.bind(KeyEvent.VK_RIGHT, "Right");
		controls.bind(KeyEvent.VK_ESCAPE, "Pause");
		controls.bind(KeyEvent.VK_SPACE, "Step");
		
		frame.addKeyListener(new KeyListener() {
			@Override
			public void keyTyped(KeyEvent e) {}

			@Override
			public void keyPressed(KeyEvent e) {
				controls.acceptKey(e.getKeyCode(), true);
			}

			@Override
			public void keyReleased(KeyEvent e) {
				controls.acceptKey(e.getKeyCode(), false);
			}
		});
	}
	
	public void show() {
		frame.setVisible(true);
		frame.createBufferStrategy(2);
	}
	
	public void present() {
		paintFrame(curFrame);
		frame.getBufferStrategy().show();
	}
	
	private void paintFrame(BufferedImage im) {
		BufferStrategy buffer = frame.getBufferStrategy();
		do {
			do {
				final int nativeX = frame.getRootPane().getX();
				final int nativeY = frame.getRootPane().getY();
				final int nativeWidth = frame.getContentPane().getWidth();
				final int nativeHeight = frame.getContentPane().getHeight();
				int multiplier = Math.min(multiplier(im.getWidth(), nativeWidth), multiplier(im.getHeight(), nativeHeight));
				
				Graphics2D g = (Graphics2D) buffer.getDrawGraphics();
				g.setBackground(Color.BLACK);
				g.clearRect(nativeX, nativeY, nativeWidth, nativeHeight);
				g.drawImage(im, nativeX, nativeY, im.getWidth()*multiplier, im.getHeight()*multiplier, frame);
				g.dispose();
			
			} while (buffer.contentsRestored());
			
			buffer.show();
			
		} while (buffer.contentsLost());
	}
	
	public int[] getRawFrame() {
		return rawFrame;
	}
	
	public BufferedImage getFrame() {
		return curFrame;
	}
	
	public void setFrame(int[] data, int width, int height) {
		BufferedImage im;
		if (curFrame!=null && curFrame.getWidth()==width && curFrame.getHeight()==height) {
			//Reuse curFrame
			im = curFrame;
		} else {
			im = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		}
		im.setRGB(0, 0, width, height, data, 0, width);
		curFrame = im;
	}
	
	private int multiplier(int vsize, int nativeSize) {
		if (vsize*2>nativeSize) return 1;
		return nativeSize / vsize;
	}

	public ControlSet getControls() {
		return controls;
	}
	
	/*
	private boolean checkBuffer() {
		if (buffered) return true;
		
		if (frame.isDisplayable()) {
			frame.createBufferStrategy(2);
			buffered = true;
			return true;
		} else {
			return false;
		}
	}*/
}
