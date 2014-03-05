package declarationInitializationScoping;

public class VariableLengthArguments {
	public static void main(String[] args) {
		System.out.println("Variable length Arguments");
		
		System.out.println(average());
		System.out.println(average(1,1,1)); 
		System.out.println(average(1.0,1,1));
		System.out.println(average(1,1,1.0)); 
	}
	
	
	public static int average(int... values){
		return 0;
	}
	public static double average(double... values){
		return 11;
	}
}
