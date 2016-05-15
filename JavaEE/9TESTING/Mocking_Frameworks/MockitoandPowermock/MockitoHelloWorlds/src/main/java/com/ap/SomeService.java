package com.ap;

import java.util.Date;

import javax.xml.ws.Holder;
/**
 * Hello world!
 *
 */
public class SomeService 
{
	
	public String getDate(){
		return new Date().toString();
	}
    
    public void methodChangeArgument(Holder<String> response){
    	if(response != null){
    		response.value = "changedddd from real code; code = 0";
    	}
    }
    
    
}
