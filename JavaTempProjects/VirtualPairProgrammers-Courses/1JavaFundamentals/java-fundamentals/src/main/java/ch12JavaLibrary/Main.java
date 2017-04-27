package ch12JavaLibrary;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Created by andrii on 27.04.17.
 */
public class Main {

    public static void main(String[] args) {
        // use Java 7 dates and calendar

        Date today = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM -- d -- Y ");
        System.out.println(sdf.format(today)); // Apr -- 27 -- 2017
        //https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html#month

        //https://docs.oracle.com/javase/7/docs/api/java/util/GregorianCalendar.html
        GregorianCalendar gCal = new GregorianCalendar(2017, 03, 27); // apr 27 2017
        System.out.println(gCal.getTime());
        gCal.add(Calendar.WEEK_OF_MONTH, 2);
        System.out.println(gCal.getTime()); // 11 may


    }
}
