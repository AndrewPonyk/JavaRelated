package part3DateAndTime;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

public class TimeZones {
    public static void main(String[] args) {
        System.out.println("Time zones example");

        Set<String> allZonesIds = ZoneId.getAvailableZoneIds(); // almost 600 zones
        //allZonesIds.forEach(System.out::println);

        ZonedDateTime warsaw = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
        ZonedDateTime Kyiv = warsaw.withZoneSameInstant(ZoneId.of("Europe/Kiev"));

        System.out.println("Now in Warsaw :" + warsaw);
        System.out.println("Now in Kyiv :" + Kyiv.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}

