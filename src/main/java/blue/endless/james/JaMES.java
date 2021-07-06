package blue.endless.james;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;

import blue.endless.james.core.catboy.GBLoader;
import blue.endless.james.core.catboy.GameBoyCore;
import blue.endless.james.host.Core;
import blue.endless.james.host.Display;
import blue.endless.james.host.PeriodicTimer;

public class JaMES {
	private static Core core;
	private static boolean framePresented = false;
	private static Display display;
	private static long now = -1L;
	private static long lastDisplay = -1L;
	//private static long frame = 0L;
	static boolean stepping = false;
	static boolean step = false;
	
	public static void main(String[] args) {
		display = new Display();
		display.show();
		
		//core = new NesCore();
		core = new GameBoyCore();
		
		try {
			//byte[] gbRom = Files.readAllBytes(new File("testcarts/dmg0_rom.bin").toPath());
			byte[] gbRom = Files.readAllBytes(new File("testcarts/gb_bios.bin").toPath());
			core.connectBios(gbRom);
			System.out.println("Connected gb bios");
			
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/if_flag_cleared.gbc")), (GameBoyCore)core); //hang
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/naughtyemu.gb")), (GameBoyCore)core); //hang
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/mem_oam.gb")), (GameBoyCore)core);
			
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/gekkio_daa.gb")), (GameBoyCore)core);
			
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/cpu_instrs.gb")), (GameBoyCore)core); //passes 6 tests
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/01-special.gb")), (GameBoyCore)core); //pass
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/02-interrupts.gb")), (GameBoyCore)core); //pass
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/03-op sp,hl.gb")), (GameBoyCore)core); //pass
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/04-op r,imm.gb")), (GameBoyCore)core); //pass
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/05-op rp.gb")), (GameBoyCore)core); //pass
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/06-ld r,r.gb")), (GameBoyCore)core); //pass
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/07-jr,jp,call,ret,rst.gb")), (GameBoyCore)core); //pass
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/08-misc instrs.gb")), (GameBoyCore)core); //pass
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/09-op r,r.gb")), (GameBoyCore)core); //pass
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/10-bit ops.gb")), (GameBoyCore)core); //pass
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/11-op a,(hl).gb")), (GameBoyCore)core); //pass
			
			//PPU tests
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/dmg-acid2.gb")), (GameBoyCore)core);
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/opus5.gb")), (GameBoyCore)core);
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/ttt.gb")), (GameBoyCore)core);
			
			//Commercial games
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/Tetris(World)(RevA).gb")), (GameBoyCore)core);
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/metroid2_world.gb")), (GameBoyCore)core);
			GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/kirbys_dream_land.gb")), (GameBoyCore)core);
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/oracle_of_seasons_us.gbc")), (GameBoyCore)core);
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/pokemon_red.gb")), (GameBoyCore)core);
			
			//INESLoader.loadCartridge(new FileInputStream(new File("donkey_kong.nes")), core);
			//INESLoader.loadCartridge(new FileInputStream(new File("testcarts/nestest.nes")), core);
			//INESLoader.loadCartridge(new FileInputStream(new File("testcarts/cpu_reset/registers.nes")), core);
			//INESLoader.loadCartridge(new FileInputStream(new File("testcarts/cpu_reset/ram_after_reset.nes")), core);
		} catch (IOException e) {
			e.printStackTrace();
		}
		core.connectControls(display.getControls());
		core.onPresentFrame().register(JaMES::presentFrame);
		
		int[] data = new int[280*240];
		data[0] = 0xFF_FF0000;
		data[1] = 0xFF_00FF00;
		data[2] = 0xFF_0000FF;
		display.setFrame(data, 280, 240);
		
		//InstructionStreamView instructionStream = new InstructionStreamView();
		//instructionStream.attach(core);
		//instructionStream.setVisible(true);
		
		core.hardReset();
		
		
		/*
		 * Let's Talk: Core t`iming strategy!
		 * 
		 * We clock on the Core's video refresh rate, whether or not that's a good fit for our own. Because we're
		 * nowhere near fullscreen exclusive mode, whatever we present will likely be filtered through a compositor and
		 * a window manager, so all we can really do is present the frames "when" the frames "happen" and hope the OS
		 * sorts things out okay. Accordingly, we will focus on the emulated hardware's "when"s and try to find the best
		 * places to wait around to equalize the two timelines.
		 * 
		 * The best-case scenario is to use PeriodicTimer; it combines the most accurate, low-granularity time source
		 * available to the system with careful handling of fractional time and jitter management. So any time the
		 * emulated system produces a frame "within reasonable time", we will then ask PeriodicTimer to sync up the time
		 * streams for us.
		 * 
		 * But what is "reasonable time"? We're talking about emulating systems from 2.8MHz up to about 28MHz - as low
		 * as 35ns per instruction, as high as 357ns. From my tests, calling System.currentTimeMillis or System.nanoTime
		 * eats up half a millisecond to a millisecond - up to 1,000,000ns! We absolutely cannot use functions which
		 * execute in these time scales on a per-cycle or per-instruction basis, we'd have no time left for emulating!
		 * 
		 * (note: I have seen some reported benchmarks of these functions as low as 25ns. I have never seen numbers this
		 * low in practice, and I live in a linux world where fast monotonic clocks are the norm. Either way, 25ns out
		 * of a 35ns budget places per-instruction timing firmly out of reach)
		 * 
		 * So rather than measuring real-world time, let's think about what that means to the emulated hardware: there
		 * is a CPU, and that CPU has a clock frequency, so a certain number of clock cycles should equate to a concrete
		 * elapsed time in the emulated timeline. If that elapsed time is equal to two cycles' worth of video refresh,
		 * then there's no video to time our emulation by and we'd better start using CPU cycles to sync up the
		 * timelines. Luckily, we know we just ate up 1/30th of a second (or whatever two divided by the video refresh
		 * rate winds up being) and can call PeriodicTimer twice to scrub forward to the equivalent moment in actual
		 * real-world time.
		 * 
		 * So, what do we need? cyclesPerFrame. We can find this conversion ratio by taking the cpu cyclesPerSecond
		 * times 1/framesPerSecond. The seconds cancel out, and we're left with cycles/frames.
		 * 
		 * For the DMG, for example, this is 4194304 * (1/59.73) == 70220 cycles per frame. Therefore if we do not
		 * receive any frames from the core after stepping it through 140442 clock cycles, we can safely skip two frames
		 * worth of real-world time and return to the top of the emulation loop.
		 */
		
		double framesPerSecond = core.getRefreshRate();
		double cyclesPerFrame = core.getClockSpeed() * (1.0/framesPerSecond);
		long cyclesPerTwoFrames = (long) (cyclesPerFrame * 2.0);
		//double millisPerFrame = 1.0 / (framesPerSecond / 1000.0);
		
		//double cyclesPerSecond = core.getClockSpeed();
		//double cyclesPerMilli = cyclesPerSecond / 1000.0;
		//long cyclesPerFourMillis = (long) (cyclesPerMilli * 4.0);
		
		//now = now();
		lastDisplay = now;
		PeriodicTimer timer = PeriodicTimer.forFPS(framesPerSecond);
		
		long toClock = 0;
		while(!core.isStopped()) {
			if (stepping) {
				if (display.getControls().get("Pause")) {
					display.getControls().lock("Pause");
					stepping = false;
				}
				if (display.getControls().get("Step")) {
					display.getControls().lock("Step");
					step = true;
				}
				
				
				display.present();
				if (step) {
					int clocked = 0;
					for(int i=0; i<10; i++) clocked+= core.clock();
					step = false;
				}
			} else {
			
				if (display.getControls().get("Pause")) {
					display.getControls().lock("Pause");
					//core.setStopped(true);
					stepping = true;
				}
				
				
				//updateOverlays();
				display.present();
				framePresented = false;
				
				long cyclesWithoutFrame = 0;
				while (!framePresented) {
					if (core.isStopped()) break;
					cyclesWithoutFrame += core.clock();
					
					if (cyclesWithoutFrame > cyclesPerTwoFrames) {
						cyclesWithoutFrame = 0;
						//updateOverlays();
						display.present();
						timer.waitForPeriod();
						timer.waitForPeriod();
					}
				}
				
				if (!core.isStopped()) {
					timer.waitForPeriod();
					lastDisplay = timer.getLastTimestamp();
				}
			}
		}
		//updateOverlays();
		display.present();
		System.out.println("Stopped.");
	}
	
	public static void updateOverlays() {;
		long elapsed = now-lastDisplay;
		BufferedImage im = display.getFrame();
		if (im!=null) {
			Graphics2D g = (Graphics2D) im.getGraphics();
			
			if (core.isStopped()) {
				g.setColor(new Color(255, 127, 127));
				//g.drawString("Core Stopped.", 16, 16);
			} else {
				g.setColor(new Color((int) (Math.random()*128), 190, 1));
				String debug = ((GameBoyCore)core).cpu.debugString;
				String[] lines = debug.split("\n");
				for(int i=0; i<lines.length; i++) {
					//g.drawString(lines[i], 2, 25+(i*16));
				}
			}
			
			g.dispose();
		}
	}
	
	public static void presentFrame(int[] frame) {
		int width = core.getFrameWidth();
		int height = core.getFrameHeight();
		if (frame.length==width*height) {
			display.setFrame(frame, width, height);
		}
		framePresented = true;
		//JaMES.frame++;
	}
	
	
	public static long now() {
		return System.nanoTime() / 1_000_000L;
	}
}
