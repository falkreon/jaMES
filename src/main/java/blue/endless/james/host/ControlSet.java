package blue.endless.james.host;

import java.util.ArrayList;

public class ControlSet {
	private ArrayList<Binding> bindings = new ArrayList<>();
	
	public void acceptKey(int key, boolean active) {
		for(Binding binding : bindings) {
			if (binding.getKey()==key) {
				binding.setActive(active);
			}
		}
	}
	
	public boolean get(String name) {
		for(Binding binding : bindings) {
			if (binding.getName().equals(name)) {
				if (binding.isActive()) return true;
			}
		}
		return false;
	}
	
	public void lock(String name) {
		for(Binding binding : bindings) {
			if (binding.getName().equals(name)) {
				binding.lock();
				return;
			}
		}
	}
	
	public ControlSet bind(int key, String name) {
		bindings.add(new Binding(name, key));
		return this;
	}
	
	private static class Binding {
		private int key;
		private boolean state;
		private String name;
		private boolean lock;
		
		public int getKey() {
			return key;
		}
		
		public boolean isActive() {
			return state & !lock;
		}
		
		public String getName() {
			return name;
		}
		
		public Binding(String name, int key) {
			this.key = key;
			this.name = name;
		}
		
		public void setActive(boolean active) {
			this.state = active;
			lock &= active;
		}
		
		public void lock() {
			lock = true;
		}
	}
}
