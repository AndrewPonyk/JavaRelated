package declarationInitializationScoping;

public class FinalMethods {
	public static void main(String[] args) {
		System.out.println("Final methods");
		
	}
}

class A {
	public static void say(){
		System.out.println("hello i am A");
	}
	
	public  void s(){
		System.out.println("s");
	}
}

class B extends A{
	public static void say(){ // ERROR  Cannot override the final method from A
		System.out.println("B");
	}
	
	
	@Override
	public void s() { // ERROR  Cannot reduce the visibility of the inherited method from A
		// TODO Auto-generated method stub
		super.s();
	}
	
}
