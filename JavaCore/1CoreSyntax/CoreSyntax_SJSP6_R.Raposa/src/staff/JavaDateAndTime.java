package staff;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class JavaDateAndTime {

	
	public static void main(String[] args) {
		calendarGetYearMonthDay();
		
		addingandSubtractingtoYearMonthDay();
		
		compareDates();
	}
	
	public static void calendarGetYearMonthDay(){
		System.out.println("calendarGetYearMonthDay");
		Calendar calendar = new GregorianCalendar();
		
		int year = calendar.get(Calendar.YEAR);
		System.out.println("Year :" + year); // 2014
		
		int month = calendar.get(Calendar.MONTH);
		System.out.println("Month : " + month); // numeration from 0
		
		int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
		System.out.println("Day of month :" + dayOfMonth); // day of month from 1 to 31
		
		int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
		System.out.println("Day of week :" + dayOfWeek);
		
		/*The day of week runs from 1 to 7 as you might expect, but sunday, not monday
		is the first day of the week. That means that 1 = sunday, 2 = monday, ..., 7 = saturday.*/
		
		int hour       = calendar.get(Calendar.HOUR);        // 12 hour clock
		int hourOfDay  = calendar.get(Calendar.HOUR_OF_DAY); // 24 hour clock
		int minute     = calendar.get(Calendar.MINUTE);
		int second     = calendar.get(Calendar.SECOND);
		int millisecond= calendar.get(Calendar.MILLISECOND);
		System.out.println("=================================");
	}
	
	public static void addingandSubtractingtoYearMonthDay(){
		System.out.println("AddingandSubtractingtoYearMonthDay");
		Calendar calendar = new GregorianCalendar(2014, 3, 28);
		System.out.println(calendar.getTime());
		
		calendar.add(Calendar.DAY_OF_MONTH, 4);
		System.out.println(calendar.getTime());
		//calendar.setTime(date)
		System.out.println("=================================");
	}
	
	public static void compareDates(){
		System.out.println("Compare dates");
		
		Calendar calendar1 = new GregorianCalendar(2014, 5, 5);
		
		Calendar calendar2 = new GregorianCalendar(2013, 5, 5);
		
		
		System.out.println(calendar1.after(calendar2));
		System.out.println("==================================");
	}
	
}
