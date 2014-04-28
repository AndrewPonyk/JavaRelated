package vogellapack;

public class String_matches {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String str = "";
		String regex = "";

		// //////////////////////// 1
		regex = "[bcr]at";
		str = "bat";
		System.out.println("1) " + str.matches(regex));
		// ////////////////////////

		// //////////////////////// 2
		regex = "[bcr]at";
		str = "rat";
		System.out.println("2) " + str.matches(regex));
		// ////////////////////////

		// ////////////////////////3
		regex = "[bcr]at";
		str = "aat";
		System.out.println("3) " + str.matches(regex));
		// ////////////////////////

		// ////////////////////////4
		regex = "[^bcr]at";
		str = "bat";
		System.out.println("4) " + str.matches(regex));
		// ////////////////////////

		// ////////////////////////5
		regex = "[a-c]at";
		str = "bat";
		System.out.println("5) " + str.matches(regex));
		// ////////////////////////

		// ////////////////////////6
		regex = "[^a-c]at";
		str = "bat";
		System.out.println("6) " + str.matches(regex));
		// ////////////////////////

		// ////////////////////////7 Intersection !!!!!!!!!!!!!
		regex = "[0-9&&[345]]";
		str = "8";
		System.out.println("7) " + str.matches(regex));
		// ////////////////////////

		// ////////////////////////8 Substracion !!!!!!!!!!!!!
		regex = "[0-9&&[^345]]";
		str = "8";
		System.out.println("8) " + str.matches(regex));
		// ////////////////////////

		// ////////////////////////9
		regex = ".";
		str = "9";
		System.out.println("9) " + str.matches(regex));
		// ////////////////////////

		// ////////////////////////10
		regex = "\\d";
		str = "9";
		System.out.println("10) " + str.matches(regex));
		// ////////////////////////

		// ////////////////////////11
		regex = "\\s";
		str = " ";
		System.out.println("11) " + str.matches(regex));
		// ////////////////////////

		// ////////////////////////12 =) match slash
		regex = "\\\\";
		str = "\\";
		System.out.println("12) " + str.matches(regex));
		// ////////////////////////

		// ////////////////////////13
		str = "True";
		System.out.println("13) " + isTrue(str));
		// ////////////////////////

		// ////////////////////////14
		str = "True";
		System.out.println("14) " + isTrueOrYes(str));
		// ////////////////////////

		// ////////////////////////15
		str = "True";
		System.out.println("15) " + isThreeLetters(str));
		// ////////////////////////

		// ////////////////////////16
		str = "295";
		System.out.println("16) " + isLessThenThreeHundret(str));
		// ////////////////////////
		
		
		System.out.println("3:3:2:2".matches("(\\d:){3}(\\d)"));
	}

	public static boolean isTrue(String s) {
		return s.matches("[tT]rue");
	}

	public static boolean isTrueOrYes(String s) {
		return s.matches("[tT]rue|[yY]es");
	}

	public static boolean containsTrue(String s) {
		return s.matches(".*true.*");
	}

	public static boolean isThreeLetters(String s) {
		return s.matches("[a-zA-Z]{3}");
		// simpler from for
		// return s.matches("[a-Z][a-Z][a-Z]");
	}

	public static boolean isNoNumberAtBeginning(String s) {
		return s.matches("^[^\\d].*");
	}

	public static boolean doesntContainb(String s) {
		return s.matches("([\\w&&[^b]])*");
	}

	public static boolean isLessThenThreeHundret(String s) {
		return s.matches("[12]?[0-9]{1,2}[^0-9]*");
	}

	public static boolean isNumeric(String string) {
		return string.matches("^[-+]?\\d+(\\.\\d+)?$");
	}
}