package genericsexamples;

public class HelloGenerics {
	public static void main(String[] args) {
		System.out.println("hello Generics");
		
        // Create a non-empty box
        Box3 box = new Box3("Hello, World!");
        System.out.println(box.toString());  // Output: Box3 [content=Hello, World!]

        // Create an empty box
        Box3 emptyBox = new Box3(null);
        System.out.println(emptyBox.toString());  // Output: Box3 is currently empty
	}
}

class Box3 {
    private Object content;

    public Box3(Object content) {
        this.content = content;
    }
	
    @Override
    public String toString() {
        if (content == null) {
            return "Box3 is currently empty";
        } else {
            return "Box3 [content=" + content.toString() + "]";
        }
    }
}

