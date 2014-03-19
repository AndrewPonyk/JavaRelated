package genericsexamples;

import java.util.ArrayList;
import java.util.List;

public class AssignTypedArrayListToArrayList {
	public static void main(String[] args) {
		ArrayList<Apple> aList = new ArrayList<Apple>();
		
		
		ArrayList bList = aList; // (1)
		bList.add("world");
		
		
		ArrayList<String> strings = new ArrayList<>();
		strings.add("hello");
		
		ArrayList justStrings = strings;
		System.out.println(justStrings.get(0));
		
		ArrayList<String> repeatString = justStrings;
		for(String item : repeatString){
			System.out.println(item);
		}
		
		
		List <? super Integer> sList = new ArrayList<Number>(); //(1)
		int i = 2007;
		sList.add(i);
		sList.add(++i); //(2)
		//Number num =  sList.get(0); // ERROR

		List<Integer> glst1 = new ArrayList(); //(1)
		List nglst1 = glst1; //(2)
		List nglst2 = nglst1; //(3)
		List<Integer> glst2 = glst1;
		
		
		List legacyList = new ArrayList<Integer>(); // (1)
		List<?> anyList = legacyList; // (2)
		legacyList = anyList; // (3)
		
	}
}

class Fruit {}
class Apple extends Fruit {}
class Orange extends Fruit {}