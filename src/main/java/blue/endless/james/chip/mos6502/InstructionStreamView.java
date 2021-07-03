package blue.endless.james.chip.mos6502;

import java.awt.BorderLayout;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import blue.endless.james.core.felines.NesCore;
import blue.endless.james.host.DebugView;

public class InstructionStreamView extends JFrame implements DebugView<NesCore> {
	private static final long serialVersionUID = 7394423336892285489L;
	NesCore core;
	JTextArea text = new JTextArea("", 0, 0);
	JScrollPane scroll = new JScrollPane(text);
	Writer streamWriter;
	
	public InstructionStreamView() {
		super("Instruction Stream");
		this.setSize(100, 500);
		this.getContentPane().add(scroll, BorderLayout.CENTER);
		text.setEditable(false);
		
	}
	
	@Override
	public void attach(NesCore core) {
		this.core = core;
		try {
			streamWriter = new FileWriter(new File("donkey_kong.log"));
			streamWriter.write("Log Start\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
		core.onInstruction().register(this::handleLine, this);
	}

	@Override
	public void detatch() {
		if (core!=null) core.onInstruction().unregister(this);
	}
	
	private void handleLine(String line) {
		String previousText = text.getText();
		try {
			streamWriter.write(line+"\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (previousText.isBlank()) {
			//text.append(line);
		} else {
			//text.append("\n");
			//text.append(line);
		}
		//scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum()-scroll.getVerticalScrollBar().getVisibleAmount());
	}
	
}