package controllers;

import play.*;
import play.mvc.*;

import java.util.*;

import models.*;

@With(Security.class)
public class Application extends Controller {

    public static void index() {
    	System.out.println(Secure.class);
    	String user = Security.connected();
        System.out.println(user);
        render();
    }
    
    public static void privatePage(){
    	renderText("private zone");
    }

    public static void auth(String login,String password){
    	Boolean logged=Security.authenticate(login, password);
    	renderTemplate("Application/index.html",logged);
    }
}