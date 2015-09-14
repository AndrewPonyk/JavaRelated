package models;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import play.data.validation.Constraints.Email;
import play.data.validation.Constraints.Required;

import com.avaje.ebean.Model;

@Entity
public class Contact extends Model {

	@Id
	@GeneratedValue
	public Long id;

	public static Finder<Long, Contact> getFinder() {
		return finder;
	}

	public static void setFinder(Finder<Long, Contact> finder) {
		Contact.finder = finder;
	}

	@Required
	public String name;

	@Required
	public String phone;

	@Required
	@Email
	public String email;

	public Contact(String name, String email, String phone){
		this.name = name;
		this.email = email;
		this.phone = phone;
	}

	public static Finder<Long, Contact> finder = new Finder<Long, Contact>(Long.class, Contact.class);

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}
}
