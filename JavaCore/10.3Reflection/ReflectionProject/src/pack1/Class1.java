package pack1;

public class Class1 {
	public int p = 10;
	private int i;

	public Class1() {

	}

	private Class1(Integer i) {
		this.i = i;
	}

	private void method1() {
		System.out.println("Class1.method1() - invoked");
	}

	private void method2(String s) {
		System.out
				.println(String.format("Class1.method2(\"%s\") - invoked", s));
	}

	private Integer method3(Integer i) {
		System.out.println(String.format("Class1.method3(%s) - invoked", i));
		return i * 2;
	}
}