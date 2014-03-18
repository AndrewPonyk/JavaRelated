package genericsexamples;

import java.util.ArrayList;
import java.util.List;

public class GenericsErasure {
	public static void main(String[] args) {
		// Java Generics erasure is really funny =)
		
		List nongeneric = new ArrayList();
		
		nongeneric.add(1);
		nongeneric.add("1");
		
		
		List<Integer> generic = new ArrayList<>();
		
		generic.add(10);
		
		System.out.println(nongeneric.getClass() == generic.getClass()); // true
		
		// we can compare Class types using ==
		/*Class is final, so its equals() cannot be overridden.
		Its equals() method is inherited from Object which reads

		public boolean equals(Object obj) {
		    return (this == obj);
		}
		So yes, they are the same thing for a Class, or any type
		which doesn't override equals(Object)

		To answer your second question, each ClassLoader can only load
		a class once and will always give you the same Class for
		a given fully qualified name.
		 * */
		
	}
}
