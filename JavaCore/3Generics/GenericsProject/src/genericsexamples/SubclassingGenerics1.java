package genericsexamples;

import java.util.ArrayList;
import java.util.Date;

public class SubclassingGenerics1 {
	public static void main(String[] args) {
		DateList holidays = new DateList();
		
		holidays.add(new Date(114, 0, 7));
		holidays.add(new Date(114, 5, 28));
		
		
		
		for(Date item : holidays){
			System.out.println(item);
		}
	}
}

class DateList extends ArrayList<Date>{
	
}
