package genericsexamples;

public class DifferenceBetweenExtendsAndSuper {
	public static void main(String[] args) {
		System.out.println("Differnce betwen <? extends> and <? super>");
		
		
	}
}

/*class Super <? super T>{  // COMPILE ERROR
	
}*/

class Extends <T extends Number>{
	
}
