package controllers;

import play.*;
import play.mvc.*;

import java.util.*;

import models.*;

public class Application extends Controller {

    public static void index() {
    	User user=new User();
    	user.name="Ivan";
    	
    	
    	
    	ArrayList<User> users=new ArrayList<>();
    	User Ivan =new User();
    	Ivan.name="Ivan";
    	
    	User Petro=new User();
    	Petro.name="Petro";
    	
    	User Roman=new User();
    	Roman.name="Roman";
    	
    	users.add(Ivan);
    	users.add(Petro);
    	users.add(Roman);
    	
    	render(user,users);
    }

}