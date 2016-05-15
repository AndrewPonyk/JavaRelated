package com.ap;

import javax.xml.ws.Holder;

public class Program {
	public static void main(String[] args) {
		SomeService someService = new SomeService();
		Holder<String> response = new Holder<String>("old value");
		System.out.println(response.value);
		
		someService.methodChangeArgument(response);
		System.out.println(response.value);
	} 
}
