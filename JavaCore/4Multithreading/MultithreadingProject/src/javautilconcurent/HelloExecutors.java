package javautilconcurent;

import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HelloExecutors {
	public static void main(String[] args) {
		ExecutorService executor = Executors.newFixedThreadPool(3);
		
		
		System.out.println(executor.getClass());
		
		for (int i = 0; i < 10; i++) {
			executor.execute(new MyThread());
		}
		

		executor.shutdown();
		while (!executor.isTerminated()) {
		}
		System.out.println("Finished all threads");

	}
}

class MyThread implements Runnable {
	
	
	@Override
	public void run() {
		
		System.out.println("Executing thread " + new Random().nextInt(100));
		return;
	}
}