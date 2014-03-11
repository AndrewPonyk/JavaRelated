package package2;

import  package1.ClassWithStaticVariable;

public class UsingStaticVariableFromClassOnAnotherPackage {
	public static void main(String[] args) {
		System.out.println("Using static variable");
		
		
		//import static package1.ClassWithStaticVariable.*;
		//System.out.println(staticvariable);
		
		//import  package1.ClassWithStaticVariable;
		System.out.println(ClassWithStaticVariable.staticvariable);;
	}
}
