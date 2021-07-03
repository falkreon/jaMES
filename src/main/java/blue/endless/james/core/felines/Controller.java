package blue.endless.james.core.felines;

import blue.endless.james.host.ControlSet;

public class Controller {
	private int shiftRegister = 0;
	
	public int read() {
		int result = shiftRegister & 0x1;
		shiftRegister = shiftRegister >> 1;
		//System.out.println("Read "+result);
		return result;
	}
	
	public void strobe(ControlSet controls) {
		shiftRegister = 0xFFFFFF00;
		if (controls!=null) {
			if (controls.get("A")) shiftRegister |= 0x01;
			if (controls.get("B")) shiftRegister |= 0x02;
			if (controls.get("Select")) shiftRegister |= 0x04;
			if (controls.get("Start")) shiftRegister |= 0x08;
			if (controls.get("Up")) shiftRegister |= 0x10;
			if (controls.get("Down")) shiftRegister |= 0x20;
			if (controls.get("Left")) shiftRegister |= 0x40;
			if (controls.get("Right")) shiftRegister |= 0x80;
		}
		//System.out.println("Final shiftRegister: "+Integer.toHexString(shiftRegister));
	}
}
