package spring.bean;

import org.springframework.stereotype.Component;

@Component
public class UserManager {
	
	public User user;
	
	public User updateUserName(User u, String newName) {	
		u.setUsername(newName);		
		return u;
	}
	
	public void throwUserUpdateExceptionMethod() throws Exception {
		System.out.println("UserManager throwUserUpdateExceptionMethod() is called");
		throw new Exception();
	}
	
	public boolean throwNotUserUpdateExceptionMethod() throws Exception{
		System.out.println("UserManager throwNotUserUpdateExceptionMethod() is called");
		return true;
	}
	
	public boolean deleteUser(User user) {
		System.out.println("UserManager deleteUser(User user) is called");
		return false;
	}

}
