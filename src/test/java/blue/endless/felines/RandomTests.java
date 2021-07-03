package blue.endless.felines;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RandomTests {
	@Test
	public void foo() {
		var start = now();
		
		for(long i=0; i<100_000_000_000L; i++) {
			var foo = 100000L * i; //trivial computation
		}
		
		var elapsed = now() - start;
		System.out.println("Elapsed: "+elapsed);
		Assertions.assertEquals(1,1);
	}
	
	private long now() { return System.nanoTime() / 1_000_000L; }
	
	/*
	@Test
	public void bar() {
		var start = now();
		var elapsed = 0L;
		var count = 0L;
		while (elapsed < 1_000L) {
			//var foo = 100000L * elapsed; //trivial computation
			count++;
			elapsed = now() - start;
			
		}
		System.out.println("Count: "+count);
	}*/
}
