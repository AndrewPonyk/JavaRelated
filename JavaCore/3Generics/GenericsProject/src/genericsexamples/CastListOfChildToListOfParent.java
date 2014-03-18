package genericsexamples;

import java.util.ArrayList;
import java.util.List;

public class CastListOfChildToListOfParent {
	public static void main(String[] args) {
		List<TestA> a  = new ArrayList<>();
		a.add(new TestB());
		a.add(new TestA());
		
		
		List<TestB> b  = new ArrayList<>();
		b.add(new TestB());
		
		
		a =(List<TestA>)(List<?>) b;

		for(TestA item : a){
			System.out.println(item.getClass()); // work nice =)
		}
	
		
		
/*		b =(List<TestB>)(List<?>) a;
		
		for(TestB item : b){
			System.out.println(item.getClass()); // will be exception if item isnt TestB
		}*/
		
		
	}
}


 class TestA {};
 class TestB extends TestA{};
 class TestC extends TestA{};