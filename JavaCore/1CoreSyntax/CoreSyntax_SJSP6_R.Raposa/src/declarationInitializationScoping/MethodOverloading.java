package declarationInitializationScoping;

public class MethodOverloading {
	public static void main(String[] args) {
		System.out.println("Method overloading");
		
		
		byte b =21;
		System.out.println(convert(b));
		/*
		 * The compiler looks for a convert method with a byte parameter. Because one doesn ’ t
exist, it looks for a convert method with a compatible parameter that a byte can be
promoted to, starting with the smallest promotion, which in this example is a short .*/
	}
	
	
	public static String convert(int x) {
		 return "int";
	}
	public static String convert(short b) {
		return "short";
	}
}	
