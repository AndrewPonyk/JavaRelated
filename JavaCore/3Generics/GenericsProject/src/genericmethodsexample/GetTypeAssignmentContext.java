package genericmethodsexample;

import java.util.ArrayList;
import java.util.List;

public class GetTypeAssignmentContext {
	
	static <T> List<T> returnT(){		
		ArrayList<T> result = new ArrayList<>();
		result.add(null);
		
		return  result;
	}
	
	
	public static void main(String[] args) {
		System.out.println("get type from context");
		
		List<Double> doubles = returnT();
		//GetTypeAssignmentContext.<String>returnT(); // CORRECT =)
		System.out.println(doubles.size());
	}
}