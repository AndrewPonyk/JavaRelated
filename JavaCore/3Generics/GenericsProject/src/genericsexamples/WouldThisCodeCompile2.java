package genericsexamples;

import java.util.ArrayList;
import java.util.List;

 class RQ100_05 {
public static void main(String[] args) {
		List<?> lst = new ArrayList<String>();
		// (1) INSERT HERE
		
		lst.add(null);
		//lst.add("OK"); Error
		//lst.add(2007);  Error
		//String v1 = lst.get(0); Error
		Object v2 = lst.get(0);
		
	}
}