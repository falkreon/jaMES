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
			GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/metroid2_world.gb")), (GameBoyCore)core);
			//GBLoader.loadCartridge(new FileInputStream(new File("testcarts/gb/kirbys_dream_land.gb")), (GameBoyCore)core);
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
		
		//long cycleGranularity = 5_000L; //At about 29780.5 cycles per frame (depending on machine), this gives us on average 6 iterations before we receive a frame
		//double framesPerSecond = 60.0;
		//long millisPerFrame = (long) (1.0/(framesPerSecond / 1000.0));
		//double cyclesPerFrame = 29780.5;
		double cyclesPerSecond = 236250000 / 11.0; //this is correct for NES but not gameboy
		double cyclesPerMilli = cyclesPerSecond / 1000.0;
		//System.out.println("Cycles/Second: "+cyclesPerSecond+", Cycles/Milli: "+cyclesPerMilli+", Millis/Frame: "+millisPerFrame);
		//Attempt to run in 4msec chunks
		long cyclesPerFourMillis = (long) (cyclesPerMilli * 4.0);
		
		//now = now();
		lastDisplay = now;
		PeriodicTimer timer = PeriodicTimer.forFPS(59.73);
		
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
				
				
				
				//try {
					updateOverlays();
					display.present();
					framePresented = false;
					//Thread.sleep(10);
					
				//} catch (InterruptedException e) {
				//	e.printStackTrace();
				//}
				
				long iterations = 0;
				while (!framePresented) {
					if (core.isStopped()) break;
					toClock += cyclesPerFourMillis;
					while(toClock>0) {
						toClock -= core.clock();
						if (core.isStopped()) break;
					}
					now = now();
					
					iterations++;
					if (iterations>22000) {
						iterations = 0;
						updateOverlays();
						display.present();
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
