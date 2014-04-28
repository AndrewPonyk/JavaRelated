package reflect;

import java.lang.reflect.Field;

import pack1.Class1;

public class JavaReflectionPerformance {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		fdieldSetValueTest();
		f();
	}
	
	public static void fdieldSetValueTest(){
		Class1 class1 = new Class1();
		Class1 cl = new Class1();
		long start = 0;
		try {
			
			start = System.currentTimeMillis();
			
			Field p = cl.getClass().getDeclaredField("p");
			p.set(class1, 1131);
			System.out.println(System.currentTimeMillis() - start);
			System.out.println(System.currentTimeMillis());
			
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}
		
	}
	
	
	public static void f(){
		Class1 class1 = new Class1();
		Class1 cl = new Class1();
		long start = 0;
		try {
			
			start = System.currentTimeMillis();
			
			Field p = cl.getClass().getDeclaredField("p");
			p.set(class1, 1131);
			System.out.println(System.currentTimeMillis() - start);
			System.out.println(System.currentTimeMillis());
			
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}
		
	}

}
