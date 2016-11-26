package vogellapack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by andrii on 23.11.16.
 */
public class GetGroups {
    public static void main(String[] args) {
        String someNumbers = "asdfasd 32 asdjaosioi 3";
        Pattern pattern = Pattern.compile("(.*)(\\d+)(.*)(\\d+)");
        Matcher matcher = pattern.matcher(someNumbers);
        System.out.println(matcher.groupCount());
        System.out.println(matcher.find());
        System.out.println(matcher.group(1));
        System.out.println(matcher.group(2));

    }
}
