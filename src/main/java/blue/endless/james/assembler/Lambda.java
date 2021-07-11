package blue.endless.james.assembler;

import java.util.List;

/**
 * Lambdas are the basic unit of compilation in jaMES. They represent precompiled code whose entrypoint is offset zero.
 * They may retain labels, but those labels are 
 */
public class Lambda {
	private final byte[] binary;
	private final Label[] labels;
	private final String architecture;
	private final String platform;
	
	/**
	 * Creates a Lambda with the specified backing code array. No defensive copying is done. No labels are associated
	 * with this code.
	 */
	public Lambda(String architecture, String platform, byte[] binary) {
		this.architecture = architecture;
		this.platform = platform;
		this.binary = binary;
		this.labels = new Label[0];
	}
	
	/**
	 * Creates a Lambda with the specified backing code and label arrays. No defensive copying is done.
	 */
	public Lambda(String architecture, String platform, byte[] binary, Label[] labels) {
		this.architecture = architecture;
		this.platform = platform;
		this.binary = binary;
		this.labels = labels;
	}
	
	/**
	 * Creates a Lambda with the specified backing code and labels. The binary array becomes the backing array of this
	 * Lambda without defensive copying; the List of labels is copied into an internal array.
	 */
	public Lambda(String architecture, String platform, byte[] binary, List<Label> labels) {
		this.architecture = architecture;
		this.platform = platform;
		this.binary = binary;
		this.labels = new Label[labels.size()];
		for(int i=0; i<this.labels.length; i++) {
			this.labels[i] = labels.get(i);
		}
	}
	
	/**
	 * Gets the backing array of this Lambda, which contains executable binary data.
	 */
	public byte[] getBinary() {
		return binary;
	}
	
	/**
	 * Get the array of Labels associated with this Lambda
	 */
	public Label[] getLabels() {
		return labels;
	}
	
	/**
	 * Returns the offset of the specified label from the start of this lambda, or -1 if this lambda has no label of
	 * this name.
	 * @param label the name of the label to search for
	 * @return the offset of the label, or -1 if no such label exists.
	 */
	public long getOffset(String label) {
		for(int i=0; i<labels.length; i++) {
			if (labels[i].name().equals(label)) return labels[i].offset();
		}
		return -1;
	}
	
	/**
	 * Returns the Instruction Set Architecture of the binary. For instance, "sm83" should be returned for gameboy
	 * binary code. "6502" should be returned for the Nintendo Entertainment System or the Atari 2600.
	 */
	public String getArchitecture() {
		return architecture;
	}
}
