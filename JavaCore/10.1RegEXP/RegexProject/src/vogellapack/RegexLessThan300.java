package vogellapack;

public class RegexLessThan300 {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String[] test = {"150", "340", "230", "95", "aaa230", "230a", "" };

		for (String item : test) {
			System.out.println(String.format("%10s is less than 300 : %s", item, isLessThan300(item)));
		}
		System.out.println();
	}

	public static boolean isLessThan300(String str){
		return str.matches("[12]?[0-9]{1,2}");
	}
}
