package controllers;

import play.*;
import play.data.validation.Required;
import play.mvc.*;

import java.util.*;



public class Application extends Controller {

    public static void index() {
        render();
    }
    
    public static void sayHello(@Required String m,String surname){
    	if(validation.hasErrors()){
    		flash.error("Oops, please enter your name!");
    		index();
    	}

    	render(m,surname);
    }

}