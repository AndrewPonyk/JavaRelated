package mainpack;

public class User {
	public String publicField = "Public value";
	private String privateProperty = "Private value";
	
	public static String STATIC_CONSTANT = "STATIC_CONSTANT Value";
	
	public String getPrivateProperty() {
		return privateProperty;
	}
	public void setPrivateProperty(String privateProperty) {
		this.privateProperty = privateProperty;
	}
	
	
	public void voidMethod(){
		System.out.println("void method");
	}
}
