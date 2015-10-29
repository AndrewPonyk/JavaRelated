package part3DateAndTime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

public class LegacyOldDate {
    public static void main(String[] args) {
        System.out.println("Legacy code using old Date");

        LocalDate nowJava8 = LocalDate.now();
        Date oldJavaDate;

        // convert LocalDate to Date
        oldJavaDate = Date.from(nowJava8.atStartOfDay(ZoneId.systemDefault()).toInstant());
        System.out.println("java.util.Date = " + oldJavaDate);

        // convert Date to LocalDate
        LocalDate java8LocalDate = oldJavaDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        System.out.println(java8LocalDate);
    }
}
