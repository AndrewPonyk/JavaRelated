package models;
import utilities.GenderType;


public class Customer {

	private String title;
	private String firstName;
	private String surname;
	private String address;
	private String phoneNumber;
	private String email;
	private int customerNumber;
	private GenderType gender;
	private boolean isvalid;

	public Customer(String title, String firstName, String surname, String address,
			String phoneNumber, String email, int customerNumber, GenderType gender) {

		setName(title, firstName, surname);
		this.address = address;
		this.phoneNumber=phoneNumber;
		this.email = email;
		this.customerNumber = customerNumber;
		this.gender = gender;
		this.isvalid = true;
	}
	
	public String getFirstName() {
		return firstName;
	}
	
	public String getSurname() {
		return surname;
	}
	
	private void setName(String title, String firstName, String surname) {
		this.title = title;
		this.firstName = firstName;
		this.surname = surname;
	}
	
	public String getMailingName() {
		String mailingName; 
		mailingName = title + " " + firstName.substring(0,1) 
				+ " " + surname;
		return mailingName;
	}
	
	public GenderType getGender() {
		return gender;
	}
		
}
