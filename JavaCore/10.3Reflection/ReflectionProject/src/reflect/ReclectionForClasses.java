package reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import javax.xml.ws.soap.MTOM;

@MTOM
@Deprecated
public class ReclectionForClasses {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// 1
		getClassObject();

		// 2
		getSuperClass();

		// 3
		gettingPackageName();

		// 4
		gettingClassModifiers();

		// 5
		getAllAnnotations();

		// 6
		getAllPublicMethods();
	}

	public static void getClassObject() {
		Class<?> integerClass1 = Integer.class;
		System.out.println(integerClass1.getCanonicalName());

		try {
			// below method is used most of the times in frameworks like JUnit
			// Spring dependency injection, Tomcat web container
			// Eclipse auto completion of method names, hibernate, Struts2 etc.
			// because ConcreteClass is not available at compile time
			Class<?> integerClass2 = Class.forName("java.lang.Integer");
			System.out.println(integerClass2.getCanonicalName());
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

		Class<?> cDouble = Double.TYPE;
		System.out.println(cDouble); // prints double

	}

	public static void getSuperClass() {
		try {
			Class<?> superClass = Class.forName("java.lang.Long")
					.getSuperclass();
			System.out.println(superClass); // prints
											// "class com.journaldev.reflection.BaseClass"
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

	}

	public static void gettingPackageName() {
		try {
			System.out.println(Class.forName("java.lang.Double").getPackage());
			System.out.println(Class.forName("java.lang.Double").getPackage()
					.getName());

		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public static void gettingClassModifiers() {
		System.out.println(Modifier.toString(ReclectionForClasses.class
				.getModifiers())); // prints "public"
		// prints "public abstract interface"
		try {
			System.out.println(Modifier.toString(Class.forName(
					"java.lang.Double").getModifiers()));
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public static void getAllAnnotations() {
		java.lang.annotation.Annotation[] annotations;
		try {
			annotations = Class.forName("reflect.ReclectionForClasses")
					.getAnnotations();
			// prints [@java.lang.Deprecated()]
			System.out.println(Arrays.toString(annotations));
			System.out.println(Class.forName("reflect.ReclectionForClasses")
					.isAnnotationPresent(Deprecated.class)); // true

		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// get public methods : static and non-static
	public static void getAllPublicMethods() {
		Method[] publicMethods;
		try {
			publicMethods = Class.forName("reflect.ReclectionForClasses")
					.getMethods();
			System.out.println(Arrays.toString(publicMethods));
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

	}

	public static void getAllPublicFields() {
		// Get All public fields
		Field[] publicFields;
		try {
			publicFields = Class.forName(
					"reflect.ReclectionForClasses").getFields();
			// prints public fields of ConcreteClass, it's superclass and super
			// interfaces
			System.out.println(Arrays.toString(publicFields));
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

	}

}
