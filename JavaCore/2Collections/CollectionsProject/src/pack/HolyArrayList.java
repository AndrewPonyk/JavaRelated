package pack;

import java.util.ArrayList;
import java.util.Arrays;

public class HolyArrayList {
	public static void main(String[] args) {
		System.out.println("Arraylist");
		
		ArrayList<String> cities = new ArrayList<>();
		
		cities.add("Lviv");
		cities.add("Kyiv");
		
		// retain = save , save in collection only "Lviv"
		cities.retainAll(Arrays.asList( new String[]{"Lviv"} ));
		for(String item : cities ){
			System.out.println(item );
		}
		
	}
}
