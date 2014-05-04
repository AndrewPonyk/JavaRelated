package customannotations;

import java.lang.reflect.Method;

public class CustomAnnotationDemo {

	public static void main(String[] args) throws Exception {

		Method[] methods = Class.forName("customannotations.AnnotatedClass")
				.getMethods();

		for (int i = 0; i < methods.length; i++) {
			if (methods[i].isAnnotationPresent(Stub.class)) {
				Stub stub = methods[i].getAnnotation(Stub.class); 
				System.out.println(stub.value() + "  " +stub.year());
			}
		}
	}

}
