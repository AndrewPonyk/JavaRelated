package com.ap;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Temp {
    public static void main(String[] args) throws InterruptedException {

        final String regex = "(-)";
        final String string = "18.10.2018 09:51:08 withdrawal visa/mc -101.80 № 153245045. card #516803***8016 ";

        final Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        final Matcher matcher = pattern.matcher(string);

        while (matcher.find()) {
            System.out.println("Full match: " + matcher.group(0));
            for (int i = 1; i <= matcher.groupCount(); i++) {
                System.out.println("Group " + i + ": " + matcher.group(i));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Thread.sleep(2000);
        LocalDateTime now1 = LocalDateTime.now();

        System.out.println(ChronoUnit.MILLIS.between(now, now1));
        System.out.println(ChronoUnit.MILLIS.between(now1, now));




    }
}