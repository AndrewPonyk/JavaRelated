package com.ap;

import javax.xml.ws.Holder;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import junit.framework.Assert;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import static org.mockito.Mockito.*;


/**
 * Unit test for simple App.
 */
public class MyTest {
	
	SomeService someService;
	
	@Before
	public void setUp(){
		someService = mock(SomeService.class);
		when(someService.getDate()).thenReturn("Mocked date");
	
		// test method which change parameters
		doAnswer(new Answer() {

			public Object answer(InvocationOnMock invocation) throws Throwable {
				 Object[] args = invocation.getArguments();
				((Holder<String>)args[0]).value = "99"; // mock method which change incomming parameter. Look code inside SomeService2 - we must mock 99
				return null;
			}
		}).when(someService).methodChangeArgument(any(Holder.class));
		
	}
	
	@Test
	public void methodChangeParametersTest(){
		System.out.println(someService.getDate()); // very simple mocking !
		
		
		SomeService2 someService2 = new SomeService2();
		someService2.someService = someService;
		
		someService2.usingMethodFromSomeService();
		
	}
}
