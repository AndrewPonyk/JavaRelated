package reflect;

import java.lang.reflect.Field;

public class ReflectionForFields {
		public int publicI = 100;
		private int privateI = 1000;
		
	public static void main(String[] args) {
		ReflectionForFields r = new ReflectionForFields();
		
		//1
		getPublicField(r, "publicI");
		
		//2
		getSetPublicFieldValue(r, "publicI");
		
		// 3
		getSetPrivateFieldValue(r, "privateI");
	}	
	
	public static void getPublicField(Object object, String fieldName){
		try {
			
			Field field = object.getClass().getField(fieldName);
			System.out.println(field.get(object));
				
		} catch (NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
	}
	
	
	public static void getSetPublicFieldValue(Object object, String propertyName){
		Field field;
		try {
			field = object.getClass().getField(propertyName);
			System.out.println(field.get(object)); //prints 5

			field.set(object, 1000);
			System.out.println(field.get(object)); 
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}

	}
	
	public static void getSetPrivateFieldValue(Object object, String propertyName){
		//We know that private fields and methods can’t be accessible
		//outside of the class but using reflection we can get/set the
		//private field value by turning off the java access check for field modifiers.
		
		try {
			Field privateField = object.getClass().getDeclaredField(propertyName);
			
			//turning off access check with below method call
			privateField.setAccessible(true);
			
			System.out.println("Private field = " + privateField.get(object)); // 
			
			privateField.set(object, 9999);
			System.out.println("Updated private field = " + privateField.get(object)); //
			
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		
	}
}
