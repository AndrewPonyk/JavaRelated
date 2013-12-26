package controllers;


import models.User;

public class Security  extends Secure.Security{
	static boolean authenticate(String username, String password){
		// username is email =)
		return User.connect(username, org.apache.commons.codec.digest.DigestUtils.md5Hex(password) ) != null;
	}

	static boolean check(String profile){
		
		if("admin".equals(profile)){
			boolean isAdmin = User.find("byEmail", connected()).<User>first().isAdmin;
			return isAdmin;
		}
		
		return false;
	}
	
	static void onDisconnected() {
	    Application.index();
	}

	static void onAuthenticated() {
	    Admin.index();
	}
}
