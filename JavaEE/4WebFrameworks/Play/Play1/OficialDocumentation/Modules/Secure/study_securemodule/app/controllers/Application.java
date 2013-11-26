package controllers;

import play.*;
import play.mvc.*;

import java.util.*;

import models.*;

@With(Secure.class)
public class Application extends Controller {

    public static void index() {
    	System.out.println(Secure.class);
    	String user = Security.connected();
        System.out.println(user);
        render();
    }
    
    
    public static void logout(){
    	try {
			Secure.logout();
		} catch (Throwable e) {
			e.printStackTrace();
		}
    	index();
    }
}