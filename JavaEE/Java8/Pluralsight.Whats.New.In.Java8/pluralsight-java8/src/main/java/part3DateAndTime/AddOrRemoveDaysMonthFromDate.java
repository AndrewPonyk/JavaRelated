package part3DateAndTime;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;

public class AddOrRemoveDaysMonthFromDate {
    public static void main(String[] args) {
        System.out.println("");

        LocalDate now = LocalDate.now();

        LocalDate nextSunday = now.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));

        System.out.println("Next sunday : " + nextSunday);

        LocalDate after10Days = now.plus(10, ChronoUnit.DAYS);
        LocalDate before10Days = now.minus(10, ChronoUnit.DAYS);

        System.out.println("10 days after today "+after10Days);
        System.out.println("10 days before today "+before10Days);
    }
}
