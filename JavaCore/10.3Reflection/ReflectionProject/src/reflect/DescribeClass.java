package reflect;

import java.lang.reflect.Field;

public class DescribeClass {

	public static void main(String[] args) throws ClassNotFoundException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		String className = "java.lang.Number";
		if(args.length > 0 ){
			className = args[0];
		}
		
		crackImmutableString();
	}

	public static void crackImmutableString() throws NoSuchFieldException,
			IllegalAccessException {
		// INSIDE String.java
		/*	    *//** The value is used for character storage. *//*
	    private final char value[];

	    *//** The offset is the first index of the storage that is used. *//*
	    private final int offset;

	    *//** The count is the number of characters in the String. *//*
	    private final int count;
*/
		
		String s = new String("hello world");
		System.out.println(s);
		char [] val = {'h','e','h'};
	
		Class sClass = s.getClass();
		
		Field value = sClass.getDeclaredField("value");
		value.setAccessible(true);
		value.set(s, val);

		System.out.println(s);

		Field count = sClass.getDeclaredField("count");
		count.setAccessible(true);
		count.set(s, 3);
		
		
		System.out.println(s);
	}
}
