package spring.bean;


//@Scope(value="session")
public class User {

	private int idUser;	
	private String username;
	private String password;
	private boolean enabled;	
	
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		System.out.println("User setUsername is called");
		this.username = username;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		System.out.println("User setPassword is called");
		this.password = password;
	}
	public boolean isEnabled() {
		return enabled;
	}
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
	public int getIdUser() {
		return idUser;
	}
	public void setIdUser(int idUser) {
		this.idUser = idUser;
	}	
	
	
	
}
