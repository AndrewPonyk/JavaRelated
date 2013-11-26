package controllers;

import models.User;


public class Security  extends Secure.Security{
	
	static boolean authenticate(String username, String password) {
		
        //User user = User.find("byEmail", username).first();
        User dev=new User();
        dev.username="dev";
        dev.password="dev";
        System.out.println("Trying to login");
        
        
        if( dev != null && dev.username.equals(username) && dev.password.equals(password)){
        	return true;
        };
        
        if( "admin".equals(username) && "admin".equals(password) ){
        	return true;
        };
        
        return false;
        
	}
	
	 static boolean check(String profile) {
	        //User user = User.find("byEmail", connected()).first();
		 	User user =new User();
		 	if(Security.connected().equals("admin")){
		 		user.username="admin";
		 		user.password="admin";
		 		user.profile="administrator";
		 	}else{
		 		user.username="dev";
		 		user.password="dev";
		 		user.profile="development";
		 	}									// getting user from fakee 'db' =) 
		 	
	        if ("administrator".equals(profile)) {
	            return user.profile.equals("administrator");
	        }
	        
	        if ("development".equals(profile)) {
	            return user.profile.equals("development");
	        }
	        
	        return false;
	 }    
}
