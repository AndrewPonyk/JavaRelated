package part3DateAndTime;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;

public class LocalDateTimeExampl {
    public static void main(String[] args) {
        System.out.println("LocalDateTime");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime some = LocalDateTime.of(1990, Month.APRIL,2,0,0);

        System.out.println(now);
        System.out.println(some);

        System.out.println(some.format(DateTimeFormatter.ofPattern("dd MMMM YYYY")));

        System.out.println("After 10 days after now : " + now.plusDays(10));
    }
}
