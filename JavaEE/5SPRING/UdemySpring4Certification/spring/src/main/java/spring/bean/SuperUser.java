package spring.bean;

public class SuperUser {
	
	private String username;
	private String password;
	private boolean active;	
	private String systemPassword;
	private String contact;
	
	public String getSystemPassword() {
		return systemPassword;
	}
	public void setSystemPassword(String systemPassword) {
		this.systemPassword = systemPassword;
	}
	public String getContact() {
		return contact;
	}
	public void setContact(String contact) {
		this.contact = contact;
	}	
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public boolean isActive() {
		return active;
	}
	public void setActive(boolean active) {
		this.active = active;
	}
	
	
}
