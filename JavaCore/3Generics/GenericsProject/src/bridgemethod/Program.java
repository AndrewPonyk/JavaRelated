package bridgemethod;

public class Program {
	public static void main(String[] args) {
		
		MyNode mn = new MyNode(5);
		Node n = mn;            // A raw type - compiler throws an unchecked warning
		n.setData("Hello");     // Causes a ClassCastException to be thrown.  
		
		//After type erasure, the method signatures do not not match. The Node method becomes setData(Object) and
		//the MyNode method becomes setData(Integer). Therefore, the MyNode setData method does
		//not override the Node setData method.

		//To solve this problem and preserve the polymorphism of generic types
		//after type erasure, a Java compiler generates a bridge method to ensure that 
		//subtyping works as expected. For the MyNode class, the compiler generates the following bridge method for setData:
		
		
		//Integer x = mn.data;
		
	}
}



