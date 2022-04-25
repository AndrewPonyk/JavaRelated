package javautilconcurent.futuretaskclass;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FutureExample {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        System.out.println("Start of main method");
        SquareCalculator squareCalculator = new SquareCalculator();

        Future<Integer> future1 = squareCalculator.calculate(100);
        Future<Integer> future2 = squareCalculator.calculate(500);

        while (!(future1.isDone() && future2.isDone())) {
            System.out.println(
                    String.format(
                            "future1 is %s and future2 is %s",
                            future1.isDone() ? "done" : "not done",
                            future2.isDone() ? "done" : "not done"
                    ));
            Thread.sleep(300);
        }

        System.out.println(future1.get());
        System.out.println(future2.get());
        squareCalculator.shutdown();
        System.out.println("End of main");

    }

    public static class SquareCalculator {
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        public Future<Integer> calculate(Integer input) {
            return executorService.submit(() -> {
                System.out.println("Calculating square for:" + input);
                Thread.sleep(1000);
                return input * input;
            });
        }

        public void shutdown() {
            executorService.shutdown();
        }
    }
}
