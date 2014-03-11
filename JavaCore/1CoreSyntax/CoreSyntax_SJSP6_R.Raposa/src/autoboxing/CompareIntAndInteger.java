package autoboxing;

import java.util.Objects;

public class CompareIntAndInteger {
	public static void main(String[] args) {
		Integer i1 = 1000;
		Integer i2 = 100;
		if(i1 == i2){
			System.out.println("i1 equals i2");
		}else {
			System.out.println("i1 not equals i2");
		}
		
		Integer a1 = 127; // Integer less 128 we can compare with == operator =))
		Integer a2 =127;
		
		if(a1 == 127){
			System.err.println("equals");
		}
		
		int in2 = 1000;
		
		if(in2 == i1){
			System.out.println("int and Integer equals");
		}
		
		System.out.println(i1.equals(in2));;
		
	}
}
