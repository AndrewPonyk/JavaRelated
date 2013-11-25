package controllers;

import models.User;


public class Security  extends Secure.Security{
	
	static boolean authenticate(String username, String password) {
		
        //User user = User.find("byEmail", username).first();
        User dev=new User();
        dev.username="dev";
        dev.password="dev";
        System.out.println("Trying to login");
        return dev != null && dev.password.equals(password);
	}
}
