package staff;

public class BitwiseOperators {
	public static void main(String[] args) {
		double d = 0.36;
		System.out.println( d > 0 && d < 1 ? d *= 100 : "not a percent");
		
		int a = 5, b = 10, c = 0;
		boolean one = a < b & c != 0;
		System.out.println(one);
		boolean two = true | true & false;
		System.out.println(two);
		boolean three = (c != 0) & (a / c > 1);
		System.out.println(three); 
		
		
	}
}
