package part2StreamsAndCollectors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

// http://www.slideshare.net/dgomezg/parallel-streams-en-java-8 presentation
// http://stackoverflow.com/questions/20375176/should-i-always-use-a-parallel-stream-when-possible
// https://dzone.com/articles/think-twice-using-java-8

public class ParallelStream {
    public static void main(String[] args) throws IllegalAccessException, InstantiationException {

        System.out.println("Example of parallel stream");
        List<String> values = new ArrayList<>();
        for(int i =0;i<100000;i++){
            values.add(Random.class.newInstance().nextInt(100)+"");
        }

        List<String> result = new ArrayList<>();

        Long start = System.currentTimeMillis();
        //values.stream().map(e->e.substring(1)).forEach(result::add);
        values.parallelStream().map(e->e.substring(1)).forEach(result::add);
        Long end = System.currentTimeMillis();

        IntStream.range(0,100000).forEach(e->{System.out.println(e +  " : " + result.get(e));});

        System.out.println();
        System.out.println(values.size() + " elements computed in " + (end - start) + " miliseconds." +
                "\n Using " + Thread.activeCount() + " threads");
    }
}


/*Java 8 Parallel Streams,describe.
        Should I always use a parallel stream when possible?*/


/*
A parallel stream has a much higher overhead compared to a sequential one.
Coordinating the threads takes a significant amount of time. I would use sequential
streams by default and only consider parallel ones if

- I have a massive amount of items to process (or the processing of each item takes time and is parallelizable)
- I have a performance problem in the first place
- I don't already run the process in a multi-thread environment (for example: in a web container,
    if I already have many requests to process in parallel, adding an additional layer of parallelism
    inside each request could have more negative than positive effects)
- In your example, the performance will anyway be driven by the synchronized access
    to System.out.println(), and making this process parallel will have no effect, or even a
    negative one. Moreover, remember that parallel streams don't magically solve all the
    synchronization problems. If a shared resource is used by the predicates and functions
    used in the process, you'll have to make sure that everything is thread-safe.
    In particular, side effects are tings you really have to worry about if you go parallel.

In any case, measure, don't guess! Only a measurement will tell you if the parallelism is worth it or not.*/
