package sortcollections;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;

public class TreeMapExample {
	public static void main(String[] args) {
		System.out.println("TreeMap is sorted collection");
		
		/*TreeMap<Integer,Integer> numb3rs = new TreeMap<>();
		
		numb3rs.put(0, 10);
		numb3rs.put(-1, 3);
		numb3rs.put(3, -1); // TreeMap - sort keys
		
		List<Integer> keys = new ArrayList<>(numb3rs.keySet());
		
		for(Integer item : keys){
			System.out.println(numb3rs.get(item)); // OUTPUT 3 10 -1 
		}*/
		
		//====================================================================
		
		// custom comparator , sort by keys
/*		TreeMap<City, String> cities = new TreeMap<>(new SortByCity());
		
		cities.put(new City(700), "Lviv");
		cities.put(new City(2300), "Kyiv");
		cities.put(new City(1100), "Odesa");
		
		Set<City> cityKeys = cities.keySet();
		
		for(City item : cityKeys){
			System.out.println(cities.get(item)); // OUTPUT : Lviv Odesa Kyiv
		}
		*/
		//==================================================
		
		/*// custom comparator , sort by values , THIS IS REALLY BAD SOLUTION
		TreeMap<String, City> towns = new TreeMap<>();
		towns.put("Strui", new City(45));
		towns.put("Rivne",new City(400));
		towns.put("Ternopil", new City(340));
		
		TreeMap<String, City> sortedTowns = new TreeMap<>(new SortByCityValues(towns));
		sortedTowns.putAll(towns);
		
		System.out.println(sortedTowns); // Strui Ternopil Rivne
		System.out.println(sortedTowns.get("Strui")); // RETURN NULL
		*/
		
		// =======================================
		// sort map By values , good solution
		
		
		
	}
}


class City {
	public int population;
	public City(int population) {
		this.population = population;
	}
}

class SortByCity implements Comparator<City>{
	@Override
	public int compare(City o1, City o2) {
		return o1.population - o2.population;
	}
}

// ReALLY bad SOLUTION
class SortByCityValues implements Comparator<String>{
	
	private Map<String, City> base;

	public SortByCityValues(Map<String, City> base) {
		this.base = base;
	}

	@Override
	public int compare(String o1, String o2) {
		if(base.get(o1).population >= base.get(o2).population){
			return 1;
		}else{
			return -1; // like method compareTo works :
			
		}
		
		
	}
}
