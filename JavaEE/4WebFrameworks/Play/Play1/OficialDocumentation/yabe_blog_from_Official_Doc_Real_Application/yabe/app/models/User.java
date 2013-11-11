package models;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import play.db.jpa.Model;

@Entity
public class User  extends Model{
	

	
	public String email;
	public String password;
	public String fullname;
	public boolean isAdmin=false;
	public User(String email, String password, String fullname) {
		super();
		this.email = email;
		this.password = password;
		this.fullname = fullname;

	}
	
	public static User connect(String email, String password) {
	    return find("byEmailAndPassword", email, password).first();
	}
	
}