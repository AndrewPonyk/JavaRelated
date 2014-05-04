package customannotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Date;

@Target({ ElementType.METHOD, ElementType.CONSTRUCTOR })
@Retention(RetentionPolicy.RUNTIME)
public @interface Stub {
	String value();
	//Date date(); // Invalid type Date for the annotation attribute Stub.date; only primitive type,
					//String, Class, annotation, enumeration are permitted or 1-dimensional arrays thereof
	int year() default 2014;    // only int , Integer - cant be here
}