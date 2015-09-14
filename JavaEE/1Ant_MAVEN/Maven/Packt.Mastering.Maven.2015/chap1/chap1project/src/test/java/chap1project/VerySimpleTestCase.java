package chap1project;

import static org.junit.Assert.*;

import org.junit.Test;

public class VerySimpleTestCase {

	@Test
	public void test() {
		int k = 134;
		assertTrue(true);
		assertEquals("hello", "hel"+"lo");
		assertEquals(134, k);
	}

}
