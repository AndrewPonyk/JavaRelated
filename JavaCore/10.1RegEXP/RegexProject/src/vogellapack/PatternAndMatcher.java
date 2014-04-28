package vogellapack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternAndMatcher {

	public static void main(String[] args) {
		String EXAMPLE_TEST = "This is my small example string which I'm going to use for pattern Matching.";

		Pattern pattern = Pattern.compile("\\w+");

		Matcher matcher = pattern.matcher(EXAMPLE_TEST);

		while (matcher.find()) {
			System.out.println(String.format(
					"Start index: %3s , end index %3s , %10s", matcher.start(),
					matcher.end(), matcher.group()));
		}

		// get matcher result count :
		/*
		 * int count = 0; while (matcher.find()) count++;
		 */

		// now create a new pattern and matcher to replace whitespace with tabs
		Pattern replace = Pattern.compile("\\s+");
		Matcher matcher2 = replace.matcher(EXAMPLE_TEST);
		System.out.println(matcher2.replaceAll("\t$0")); // $0 here is a finded
															// group

		// ////////////
		groups();

	}

	public static void groups() {

		// Example from thinking in java
		String poem = "Twas brillig, and the slithy toves\n"
				+ "Did gyre and gimble in the wabe.\n"
				+ "All mimsy were the borogoves,\n"
				+ "And the mome raths outgrabe.\n\n"
				+ "Beware the Jabberwock, my son,\n"
				+ "The jaws that bite, the claws that catch.\n"
				+ "Beware the Jubjub bird, and shun\n"
				+ "The frumious Bandersnatch.";

		Matcher m = Pattern.compile("(?m)(\\S+)\\s+((\\S+)\\s+(\\S+))$")
				.matcher(poem);

		while (m.find()) {
			System.out.println(m.groupCount());
			for (int j = 0; j <= m.groupCount(); j++)
				System.out.print("[" + m.group(j) + "]");
			
			System.out.println();
		}
	}
}
