package blue.endless.james.assembler;

import java.util.List;

public class Lambda {
	private final byte[] binary;
	private final Label[] labels;
	
	/**
	 * Creates a Lambda with the specified backing code array. No defensive copying is done. No labels are associated
	 * with this code.
	 */
	public Lambda(byte[] binary) {
		this.binary = binary;
		this.labels = new Label[0];
	}
	
	/**
	 * Creates a Lambda with the specified backing code and label arrays. No defensive copying is done.
	 */
	public Lambda(byte[] binary, Label[] labels) {
		this.binary = binary;
		this.labels = labels;
	}
	
	/**
	 * Creates a Lambda with the specified backing code and labels. The binary array becomes the backing array of this
	 * Lambda without defensive copying; the List of labels is copied into an internal array.
	 */
	public Lambda(byte[] binary, List<Label> labels) {
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
}
