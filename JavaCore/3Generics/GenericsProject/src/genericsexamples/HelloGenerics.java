package genericsexamples;

public class HelloGenerics {
	public static void main(String[] args) {
		System.out.println("hello Generics");
		
		Box<String> box = new Box<>();
		
		box.add("some element");
		
		System.out.println(box.getT());
		
	}
}

class Box <T>{
	T t;
	
	public void add(T t){
		this.t = t;
	}
	
	public T getT(){
		return this.t;
	}
}