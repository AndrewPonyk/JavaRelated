package ch6CompareStrings;

import java.util.Scanner;

/**
 * Created by andrii on 22.04.17.
 */
//https://www.oreilly.com/learning/java7-features interesting
public class Main {
    public static void main(String[] args) {
        //compare String with null
        System.out.println("hello".equals(null)); // false
     
        // 
        String myName = "Matt Joh";
        String yourName = "Matt Gqa";
        String myFirstName = myName.substring(0, 4);
        String yourFirstName = yourName.substring(0, 4);
        
        System.out.println(myFirstName == yourFirstName); // it is false because substring uses new String(), and 
        // returned objects are not in string pool
        
        System.out.println(myFirstName.intern() == yourFirstName.intern()); // true, intern() method interning stings, ensure that value will be in string pool

        
        // String in switch
        String val = new Scanner(System.in).nextLine();
        switch (val) {
		case "1":
			System.out.println("You print 1");
			break;
		case "2":
			System.out.println("You print 2");
			
		default:
			System.out.println("You print something else");
			break;
		}
    }
}