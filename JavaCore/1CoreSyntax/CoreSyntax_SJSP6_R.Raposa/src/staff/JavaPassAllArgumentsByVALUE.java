package staff;

public class JavaPassAllArgumentsByVALUE {
	
	public static void main(String[] args) {
		System.out.println("");
		
		Editor ed = new Editor("1234");
		System.out.println(ed);
		tryToChangeReference(ed);
		System.out.println(ed);
		//
		String s = "123";
		tryToChangeString(s);
		System.out.println(s);
	}
	
	// !!! We can't change reference address inside method
	public static void tryToChangeReference(Editor e){
		
		e = new Editor("eeeeeee");
		System.out.println("Object inside method : " + e);
	}
	
	// this method doesnt affect origin string value
	public static void tryToChangeString(String str){
		str = ".";
	}
}

class Editor{
	private String name;
	
	public Editor(String name) {
		this.name = name;
	}
	
	@Override
	public String toString() {
		return this.name;
	}
}