package staff;


public class Assert {
	public static void main(String [] args) {
		Integer x = 10;
		
		//the command line does not enable assertions
		assert x == null && x >= 0;
		System.out.println(x);
	}
}
