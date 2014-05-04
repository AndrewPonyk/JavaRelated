package tips;
	
import examplesjavacode.Computation;
public class DebugBytecode {
	
	public int intVal;
	public int[] intmas;
	
	public void whileLoop(){
		
		int i=0;
		while (i<2){
			i++;
		}
	}
	
	public void simpleMethod(){
		long i;
		i = 100;
		
		if(i == 100){
			int k = 10;
		}
		
		System.out.println(i);
		
			// simpleMethod in Bytecode :
		/*      0 bipush 100;
		        2 istore_1;                i 
		        3 iload_1;                 i 
		        4 bipush 100;
		        6 if_icmpne 6;
		        9 bipush 10;
		        11 istore_2;
		        12 getstatic 15;           java.lang.System.out 
		        15 iload_1;                i 
		        16 invokevirtual 21;       void println(int arg0) 
		        19 return;
		*/
	}
	
	public void simleMethodWhichCallAnotherMethod(){
		simpleMethod();
		int i = 100; 
		return;
		
		
	}
	
	public int methodWithArray(int arg1){
		int [] mas = new int[3];
				mas[0]= 33;
				mas[1] = 66;
		System.out.println(mas.length);
		return 999;
	}
	
	public void methodWithCreatingNewStrings(){
		String simplyString = "echo";
		
		String newString = new String("new string content");
		
		String join = simplyString + newString;
	}
	
	public static void main(String[] args) {
		int k =0 ;
		// TO debug BYTECODE you need 
		//1 Bytecode - in .jar or in .class  added to classpath
		
		//2 Intalled Dr.Garbage Plugin
		
		//3 Associate Dr.Gargage with .class files  : go to preferences -> file association -> select .class files without source and set default
		
		//4 set breakpoint , and vuala you can debug bytecode =) 
		Computation comp = new Computation();
		System.out.println( comp.performSomeAction(4) ); 
		System.out.println("hello");
	}
}	