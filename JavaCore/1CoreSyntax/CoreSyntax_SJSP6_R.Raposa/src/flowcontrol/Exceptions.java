package flowcontrol;

public class Exceptions {
	
	public static void withFinally(){
		int a = 10;
		try{
			if(a== 10) throw new AssertionError();
		}
		catch(AssertionError e){
			System.out.println("Caching");
			return;
		}
		finally{ // hovewer in cach we make return , but finally is executed
			System.out.println("inside finally");
		}
		
	}
	
	public static void main(String[] args) {
		withFinally();
	}
}
