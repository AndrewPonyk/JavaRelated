package controllers;

import play.*;
import play.data.validation.Required;
import play.mvc.*;

import java.util.*;

import models.*;

public class Application extends Controller {

    public static void index() {
        render();
    }
    
    public static void sayHello(@Required String m){
    	if(validation.hasErrors()){
    		flash.error("Oops, please enter your name!");
    		index();
    	}
    	
    	render(m);
    }

}