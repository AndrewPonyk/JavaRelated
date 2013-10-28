package controllers;

import play.*;
import play.data.validation.Check;
import play.data.validation.CheckWith;
import play.data.validation.Error;
import play.data.validation.Required;
import play.mvc.*;

import java.util.*;

import play.data.validation.Min;


public class Application extends Controller {

    public static void index() {
        render();
    }

    public static void validate (String name,Integer age,String password){
    	
    	render(name,age);
    }

    public static void process(String name, Integer age ,
    		@Required @CheckWith(MyPasswordCheck.class) String password){    	
        validation.required(name);
        validation.required(age);
        validation.min(age, 10); // we can Annotations , or these methods
 
    	
    	
    	
        if(validation.hasErrors()){
        	   params.flash(); // add http parameters to the flash scope
               validation.keep(); // keep the errors for the next request
               validate(name,age,password);
        }

        renderText("Ok , everything is OK");
    }    
    
    static class MyPasswordCheck extends Check {
		@Override
		public boolean isSatisfied(Object validatedObject, Object value) {
			
			setMessage("validation.password","8");
			if(value==null) return false;
			
			return (value.toString().length()>=8) && value.toString().matches(".*[0-9].*");	
		}
    	
    }
}