package timedate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class HelloJava8TimeDate {

    public static void main(String[] args) {
        String dateTime = "2018-06-04 22:42:12.0";
        LocalDateTime now = LocalDateTime.now();
        System.out.println(now);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S");
        LocalDateTime parse = LocalDateTime.parse(dateTime, formatter);
        System.out.println(parse.getHour());
    }
}