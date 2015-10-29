package part4StringsBitsAndOther;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

public class ReadingFilesWithJava8 {
    public static void main(String[] args) {
        System.out.println("Reading file in java 8 ");

        Instant begin = Instant.now();

        try (BufferedReader buf = new BufferedReader(new FileReader("/home/andrew/log.txt"))) {
            System.out.println("Lines in file which contains 'error' ");
            buf.lines().filter(e -> e.toLowerCase().contains("error")).forEach(System.out::println);
            Instant end = Instant.now();
            System.out.println("Operation takes " + Duration.between(begin, end).toMillis() + " miliseconds");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
