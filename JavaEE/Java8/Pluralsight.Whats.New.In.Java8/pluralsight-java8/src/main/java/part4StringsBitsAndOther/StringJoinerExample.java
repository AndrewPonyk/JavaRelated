package part4StringsBitsAndOther;


import java.util.StringJoiner;

public class StringJoinerExample {
    public static void main(String[] args) {
        System.out.println("Joining string in java 8");

        StringJoiner sj = new StringJoiner(",", "{", "}");
        sj.add("one").add("two").add("three");
        System.out.println(sj.toString());

        // String.join uses StringJoiner under the hood
        System.out.println(String.join(";", new String[]{"1", "2", "3"}));
    }
}
