package docsOracleandLearningJava4ed;

public class ReturnFinally {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		mehtodWithFinally();
	}

	
	public static void mehtodWithFinally(){
		try {
			return;
		}finally{
			System.out.println("Finally is executed, even after return");
		}
	}
}
