package docsOracleandLearningJava4ed;

import java.io.IOException;
import java.io.InputStreamReader;

public class CatchingMoreThanOneTypeParameterIsFinal {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}
	
	public static void catchingOneType(){
		try {
			InputStreamReader r = new InputStreamReader(null);
		} catch (Exception e) {
			e = null; // ok 
		}
	}
	
	public static void catchingMoreThanOneType(){
		try {
			InputStreamReader r = new InputStreamReader(null);
			r.read();
		} catch (IOException|NullPointerException e) {
			//e = null; // The parameter e of a multi-catch block cannot be assigned
		}
	}

}
