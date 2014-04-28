package vogellapack;

public class GroupingandBackreference {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		//remove white spaces between world and following comma , dot or dvikrapky
		String pattern = "(\\w)(\\s+)([\\.,:])";
		
		String str = " Hello world , and once more    : Hello world. ";
		
		System.out.println(str);
		System.out.println(str.replaceAll(pattern, "$1$3")); // numeration from 1
		
		
		/////////////////////////////////////////////////////////////////////
		//This example extracts the text between a title tag.
		String EXAMPLE_TEST = "asdfioasdfi<title>TiTlE</title>asdfasdf";
		// Extract the text between the two title elements
		pattern = "(.*)(<title.*?>)(.+?)(</title>)(.*)";
		String updated = EXAMPLE_TEST.replaceAll(pattern, "$3"); 
		System.out.println(updated);
		
	}
}
