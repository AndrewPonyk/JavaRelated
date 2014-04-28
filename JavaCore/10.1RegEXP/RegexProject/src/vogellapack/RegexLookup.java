package vogellapack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexLookup {

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		// here we have negative lookup
		String regex = "(John) (?!Smith)([A-Z]\\w+)";
		Pattern pattern = Pattern.compile(regex);

		String candidate = "I think that John Smith ";
		candidate += "is a fictional character. His real name ";
		candidate += "might be John Jackson, John Westling, ";
		candidate += "or John Holmes for all we know.";
		
		Matcher matcher = pattern.matcher(candidate);
		
		while (matcher.find()) {
			System.out.println(matcher.group(2));
		}
	}
	
	
	
	public static void nonCapturingGroups(){
		String regex = "John (?!Smith)[A-Z]\\w+";
		Pattern pattern = Pattern.compile(regex);
		
		String candidate = "I think that John Smith ";
		candidate += "is a fictional character. His real name ";
		candidate += "might be John Jackson, John Westling, ";
		candidate += "or John Holmes for all we know.";
		
		
	}

}
