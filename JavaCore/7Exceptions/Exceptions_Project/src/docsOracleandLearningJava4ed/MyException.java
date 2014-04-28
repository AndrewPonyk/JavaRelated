package docsOracleandLearningJava4ed;

import java.io.IOException;
import java.util.Scanner;

public class MyException extends Exception {
	private static final long serialVersionUID = 4664456874499611218L;

	private String errorCode = "Unknown_Exception";

	public MyException(String message, String errorCode) {
		super(message);
		this.errorCode = errorCode;
	}

	public String getErrorCode() {
		return this.errorCode;
	}

	public static void main(String[] args) {
		
		Scanner s = new Scanner(System.in);
		int result = s.nextInt();
		
			try {
				if(result > 10){
					throw new MyException("My exception", "0");
				}
			} catch (MyException e) {
				e.printStackTrace();
			}
		}
	}
