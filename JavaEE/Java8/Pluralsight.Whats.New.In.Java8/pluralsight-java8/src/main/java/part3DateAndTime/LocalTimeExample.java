package part3DateAndTime;

import java.time.LocalTime;

public class LocalTimeExample {
    public static void main(String[] args) {
        System.out.println("LocalTime class example");

        LocalTime now = LocalTime.now();
        System.out.println(now );

        LocalTime after10Hours = now.plusHours(10);
        System.out.println("After 10 hours " + after10Hours);



    }
}
