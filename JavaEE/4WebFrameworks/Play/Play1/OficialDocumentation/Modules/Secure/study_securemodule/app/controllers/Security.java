package controllers;

import org.apache.commons.codec.digest.DigestUtils;

import models.User;


public class Security  extends Secure.Security{
	
	static boolean authenticate(String username, String password) {
		
        User user = User.find("byUsername", username).first();
        String passHash=DigestUtils.md5Hex(password);
        	
        System.out.println("Trying to login");
        
        if( user != null && user.username.equals(username) && user.password.equals(passHash)){
        	return true;
        };
        
        return false; 
	}
	
	 static boolean check(String profile) {
	        User user = User.find("byUsername", connected()).first();
			// getting user from  'db' =) 
		 	
	        if ("administrator".equals(profile)) {
	            return user.profile.equals("administrator");
	        }
	        
	        if ("user".equals(profile)) {
	            return user.profile.equals("user");
	        }
	        return false;
	 }    
}
