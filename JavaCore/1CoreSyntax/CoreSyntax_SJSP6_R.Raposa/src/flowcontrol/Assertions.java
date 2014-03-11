package flowcontrol;

public class Assertions {
	public static void main(String[] args) {
		System.out.println("Using Assertions");
		
		int a =10;
		
		/*By default, assert statements are ignored by the JVM at runtime. To enable assertions, use
		the - enableassertions fl ag on the command line:
		   java -enableassertions Rectangle
		
		You can also use the shortcut - ea fl ag:
		java -ea Rectangle*/
		
		assert a>40 : "something wrong";
	}
}
