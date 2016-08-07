package part3DateAndTime;

import java.time.LocalDate;
import java.time.Month;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class LocalDateandPeriodandFormatter {
    public static void main(String[] args) {
        System.out.println("LocalDate and Period");

        LocalDate now = LocalDate.now();
        LocalDate shakeSpeare = LocalDate.of(1564, Month.APRIL, 23);
        LocalDate my = LocalDate.of(1992, Month.APRIL, 2);

        System.out.println("Now is : " + now);
        // print date as "26/09/2015"
        System.out.println("Formatted Now is : " + now.format(DateTimeFormatter.ofPattern("dd/MM/uuuu")));

        System.out.println(my.until(now, ChronoUnit.DAYS));
    }
}
