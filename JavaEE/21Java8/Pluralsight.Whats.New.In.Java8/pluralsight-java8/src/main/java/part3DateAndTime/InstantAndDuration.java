package part3DateAndTime;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class InstantAndDuration {
    public static void main(String[] args) {
        System.out.println(" Instant and Duration classes ");
        // java.time  package replaces java.util.Date class and other
        int k =0;
        List<Integer> list = new ArrayList<>();

        Instant begin = Instant.now();
        long oldBegin = System.currentTimeMillis();

        for(int i = 0;i<1000;i++){ // takes 3 miliseconds
           k += i * i;
            if(k%3 == 0){
                list.add(k);
            }
        }
        //java.util.stream.IntStream.range(0,1000).forEach(e->{ int kk = e * e; if(e%3 == 0 ){ list.add(kk);}}); // takes 105 miliseconds

        Instant end = Instant.now();
        long oldEnd = System.currentTimeMillis();

        System.out.println(k);
        System.out.println("Calculation takes ");
        System.out.println((oldEnd - oldBegin) + " miliseconds"); // new Way in java 8 , using Instant and Duration classes
        System.out.println(Duration.between(begin, end).getNano() + " nanoseconds");

    }
}
