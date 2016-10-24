package spring.bean;


public class RegisterService {


	
	public boolean registerUser(User user) {	
		System.out.println("RegisterService registerUser(User u) is called");
		if(user.getUsername().equals("") || user.getPassword().equals(""))
	    return false;
		else
		return true;
	}


}
