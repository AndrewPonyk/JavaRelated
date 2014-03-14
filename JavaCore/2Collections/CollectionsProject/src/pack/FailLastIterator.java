package pack;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;

public class FailLastIterator {
	public static void main(String[] args) {
		System.out.println("Fail last iterator");
		
		HashMap<String, String> cities = new HashMap<>();
		
		cities.put("Lviv", "700");
		cities.put("Kyiv", "2700");
		
		Iterator it = cities.entrySet().iterator();
		
		
		//it.next();
		//cities.put("a", "b");
		// System.out.println(it.next()); // Exception
		

		
		
		
		Hashtable<String, String> citiesHash = new Hashtable<>();
		
		citiesHash.put("Lviv", "700");
		citiesHash.put("Kyiv", "2700");
		
		Iterator itHash = citiesHash.entrySet().iterator();
		
		
	/*	itHash.next();
		citiesHash.put("a", "b");
		System.out.println(itHash.next());*/
		
		//Enumeration keys = citiesHash.keys(); 
		 for (Enumeration e = citiesHash.keys() ; e.hasMoreElements() ;) {
			 System.out.println(e.nextElement());
			 citiesHash.put("a", "b"); //this is ok / This iterator doesnt throw exception
		 } 
		 
		
	}
}
