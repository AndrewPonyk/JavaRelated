package models;

public class Client {
	
	public String name="";
	public String secondName="";
	
	@Override
	public String toString() {
		return this.name+" "+this.secondName;
	}
}
