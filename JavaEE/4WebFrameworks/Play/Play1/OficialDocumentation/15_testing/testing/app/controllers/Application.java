package controllers;

import play.*;
import play.mvc.*;

import java.util.*;



public class Application extends Controller {

    public static void index() {
    	
        render();
    }
    
    public static void autorization(String login, String password){
    	if(login!=null && login.equals("dev")
    			&&password!=null && password.equals("dev")){
    		render(login);
    	}else{
    		renderArgs.put("autorizationMessage", "Wrong credentials");
    		index();
    	}
    }
}