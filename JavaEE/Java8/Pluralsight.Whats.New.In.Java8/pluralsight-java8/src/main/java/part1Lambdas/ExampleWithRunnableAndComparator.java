package part1Lambdas;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Created by andrew on 17.09.15.
 */
public class ExampleWithRunnableAndComparator {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Runnable and Comparator");

        Runnable runnableLambda = ()->{
            for(int i =0; i < 3 ; i++ ){
                System.out.println("Hello from thread" + Thread.currentThread().getName());
            }
        };
        System.out.println("runnableLambda = " + runnableLambda.hashCode());
        Thread t = new Thread(runnableLambda);
        t.start();
        t.join();

        /////////////////////////
        List<String> list = Arrays.asList("***", "**", "****", "*");
        Comparator<? super String> comp = (o1, o2) -> {
            //return Integer.compare(o1.length(), o2.length());
            return o1.length() - o2.length();
            // -1 if first argument is less ,0 when equals , 1 when greater than second argument

        };
        Collections.sort(list, comp);

        for(String item : list){
            System.out.println(item);
        }


    }
}
