package reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import pack1.Class1;

public class ReflectionForMethodsAndConstructors {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		//1
		invokePublicMethod();
		
		System.out.println("=======");
		
		//2 
		Class1 class1 = new Class1();
		invokePrivateMethod(class1,"method1");
		invokePrivateMethodWithParameters(class1, "method2", "HELLO");
		invokePrivateMethodWithParameterAndResult(class1, "method3", 1001);
		
		//3 
		System.out.println("========");
		instantiateObjectUsingConstructor("pack1.Class1");
	}
	
	public static void getPublicMethod(){
		Method method;
		try {
			method = Class.forName("java.util.HashMap").getMethod("put", Object.class, Object.class);
			//get method parameter types, prints "[class java.lang.Object, class java.lang.Object]"
			System.out.println(Arrays.toString(method.getParameterTypes()));
			//get method return type, return "class java.lang.Object", class reference for void
			System.out.println(method.getReturnType());
			//get method modifiers
			System.out.println(Modifier.toString(method.getModifiers())); //prints "public"
		
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void invokePublicMethod(){
		Method method;
		try {
			method = Class.forName("java.util.HashMap").getMethod("put", Object.class, Object.class);
		
			Map<String, String> hm = new HashMap<>();
			method.invoke(hm, "key1", "value1");
			System.out.println(hm); // prints {key=value}
		} catch (NoSuchMethodException | SecurityException
				| ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
	}
	
	public static void invokePrivateMethod(Object object, String method1){
		
		try {
			Method privateMethod1 = object.getClass().getDeclaredMethod(method1, null);
			privateMethod1.setAccessible(true);
			
			privateMethod1.invoke(object,null);
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
	}
	
	public static void invokePrivateMethodWithParameters(Object object, String method2, String parameter){
		try {
			Method privateMethod2 = object.getClass().getDeclaredMethod(method2, String.class);
			privateMethod2.setAccessible(true);
				
			privateMethod2.invoke(object, parameter);
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}	
	}
	
	public static void invokePrivateMethodWithParameterAndResult(Object object, String method3, Integer parameter){
		try {
			Method privateMethod3 = object.getClass().getDeclaredMethod(method3, Integer.class);
			privateMethod3.setAccessible(true);
			
			Object result = privateMethod3.invoke(object, parameter);
			System.out.println(result);
			
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
		}
	}
	
	public static void instantiateObjectUsingConstructor(String className){
		try {

			Constructor<?> constructor = Class.forName(className).getDeclaredConstructor(Integer.class);
			//Class.forName(className).getConstructor(Integer.class);// DOESTN WORK FOR PRIVATE CONSTRUCTORS
			constructor.setAccessible(true);
			
			Object myObj = constructor.newInstance(999);
			invokePrivateMethodWithParameterAndResult(myObj, "method3", 555);
			
		} catch (NoSuchMethodException | SecurityException
				| ClassNotFoundException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
	}
}
