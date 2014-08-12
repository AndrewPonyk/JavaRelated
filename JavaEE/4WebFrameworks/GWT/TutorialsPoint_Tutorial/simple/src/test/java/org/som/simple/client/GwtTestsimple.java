package org.som.simple.client;

import org.junit.Test;
import org.som.simple.shared.FieldVerifier;

import com.google.gwt.core.client.GWT;
import com.google.gwt.junit.client.GWTTestCase;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.ServiceDefTarget;

public class GwtTestsimple extends GWTTestCase {

	@Override
	public String getModuleName() {
		//return "org.som.simple.simpleJUnit";
		//return "org.som.simple.simple";
		return null;
	}

	@Test
	public void testTemp() {
		assertEquals(true, true);
		assertEquals(true, true);
	}
	
	public void testInMethodName(){
		assertEquals(true, true);
	}
}