package docsOracleandLearningJava4ed;

import java.io.IOException;

public class Throws {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}
	
	//ok
	public static void useThrows() throws IOException{
		
		throw new IOException();
	}
	
	//ok
	public static void useThrows2() {
		
		throw new NullPointerException();
	}
	
	// compile error
	//public static void useThrows3() {
		
		//throw new IOException();
	//}
	

}
