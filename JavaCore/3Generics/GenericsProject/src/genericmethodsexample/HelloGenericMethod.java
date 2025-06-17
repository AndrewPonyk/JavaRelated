package genericmethodsexample;

import java.util.Arrays;
import java.util.List;

public class HelloGenericMethod {
	
	
	 // Determine if an object is in an array. 
	static <T, V extends T> boolean isIn(T x, V[] y) { 
	 
	    for(int i=0; i < y.length; i++) 
	      if(x.equals(y[i])) return true; 
	 
	    return false; 
	} 
	
	public static <T extends Printable> void printGeneric(T p){
		p.print();
	}
	
	public static void main(String[] args) {
		
		HelloGenericMethod.printGeneric(new CanonMP210());
		
		List<String> ls1 = Arrays.asList("one", "two");
		List<String> ls2 = Arrays.asList("three", "four");
		List<String>[] aols = new List[10];
	}
}


interface Printable {
	public void print();
}

class CanonMP210 implements Printable{
	@Override
	public void print() {
		System.out.println("Canon mp 210");
	}
}

class CanonMP250 implements Printable{
	@Override
	public void print() {
		System.out.println("Canon mp 250");
	}
}

class Box3<T> {
    private T data;

    public Box3(T data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "Box3{" +
                "data=" + data +
                '}';
    }
}

public class TestBox3 {
    public static void main(String[] args) {
        Box3 emptyBox = new Box3(null);
        System.out.println(emptyBox.toString());
    }
}
