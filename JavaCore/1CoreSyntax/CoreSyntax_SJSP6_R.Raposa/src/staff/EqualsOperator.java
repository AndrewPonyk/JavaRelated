package staff;

public class EqualsOperator {
	public static void main(String[] args) {
		 int x = 57;
		 float f = 57.0f ;
		 double d = 5.0;
		 boolean b = false;
		
		 boolean one = x == 57;
		 System.out.println(one);
		 boolean two = (f != d);
		 System.out.println(two);
		 boolean three = (b = true);
		 System.out.println(three);
	}
}
