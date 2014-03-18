package genericsexamples;

import java.io.IOException;

public class ThrowsGenericParameter {
	public static void main(String[] args) throws IOException {
		try {
			new ExceptionTester<IOException>().throwParameter(new IOException());;
		} catch (IOException e) {
			System.out.println(e);
		}
		
		
	}
}

class ExceptionTester <T extends Throwable> {
	public void throwParameter(T e) throws T{
		throw e;
	}
}