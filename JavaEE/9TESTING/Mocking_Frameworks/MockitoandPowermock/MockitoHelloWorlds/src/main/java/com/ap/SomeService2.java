package com.ap;

import javax.xml.ws.Holder;
/**
 * Hello world!
 *
 */
public class SomeService2 
{
    public SomeService someService;
	
	public void usingMethodFromSomeService(){
		Holder<String> response = new Holder<String>();
		someService.methodChangeArgument(response);
		if(response.value.toString().equals("99")){
			System.out.println("no problem )))");
		}
	}
	
}
