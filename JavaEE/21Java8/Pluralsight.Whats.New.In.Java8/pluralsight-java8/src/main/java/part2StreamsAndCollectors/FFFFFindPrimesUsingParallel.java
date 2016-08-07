package part2StreamsAndCollectors;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class FFFFFindPrimesUsingParallel {
    public static void main(String[] args) {
        System.out.println("Find prime numbers in range from 0 to N");

        Instant begin = Instant.now();
        System.out.println(FFFFFindPrimesUsingParallel.countPrimes(10_000_000));
        Instant end = Instant.now();
        System.out.println("Operation takes " + Duration.between(begin, end).toMillis());
    }

    private static long countPrimes(int max) {
        // 40 seconds, when (0 10_000_000)
        //return LongStream.range(1, max).filter(FFFFFindPrimesUsingParallel::isPrime).count();


        // 24 seconds, when (0 10_000_000)
        return LongStream.range(1, max).parallel().filter(FFFFFindPrimesUsingParallel::isPrime).count();
    }
    private static boolean isPrime(long n) {
        return n > 1 && LongStream.rangeClosed(2, (long) Math.sqrt(n)).noneMatch(divisor -> n % divisor == 0);
    }
}
