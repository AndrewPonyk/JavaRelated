package book808questions.chap3CoreJavaAPIs;

import java.time.*;

/**
 * Created by 4G-PC on 19.09.2016.
 */
public class Java8DateTime {
    public static void main(String[] args) {
        /*LocalDate Contains just a date—no time and no time zone. A good example of
            LocalDate is your birthday this year. It is your birthday for a full day regardless of what
            time it is.

        LocalTime Contains just a time—no date and no time zone. A good example of
        LocalTime is midnight. It is midnight at the same time every day.

        LocalDateTime Contains both a date and time but no time zone. A good example of
        LocalDateTime is “the stroke of midnight on New Year’s.” Midnight on January 2 isn’t
        nearly as special, and clearly an hour after midnight isn’t as special either.
        * */

        System.out.println(LocalTime.now());

        //Both of these examples
        //create the same date:
        LocalDate date1 = LocalDate.of(2015, Month.JANUARY, 20);
        LocalDate date2 = LocalDate.of(2015, 1, 20);


        // interesting way to create LocalDateTime
        LocalTime nowTime = LocalTime.now();
        LocalDate date = LocalDate.of(2015, Month.APRIL, 2);
        LocalDateTime years23 = LocalDateTime.of(date, nowTime);
        System.out.println(years23);


        // It is common for date and time methods to be chained
        LocalDateTime dateTime = LocalDateTime.of(date2, nowTime)
                .minusDays(1).minusHours(10).minusSeconds(30);
        System.out.println(dateTime);

        // Interesting Period class
        Period.ofWeeks(1);
        System.out.println(LocalDate.now().plus(Period.ofWeeks(1)));
    }
}
