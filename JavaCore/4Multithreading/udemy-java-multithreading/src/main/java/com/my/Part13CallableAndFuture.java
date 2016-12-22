package com.my;

import java.util.concurrent.*;

/**
 * Created by andrii on 27.11.16.
 */
public class Part13CallableAndFuture {
	public static void main(String[] args) {
		System.out.println("Java8 eclipse");
		ExecutorService executor = Executors.newCachedThreadPool();

		Future<Integer> future = executor.submit(new Callable<Integer>() {
			@Override
			public Integer call() throws Exception {
				System.out.println("Starting...");
				Thread.sleep(700);
				System.out.println("Finished.");
				return 192;

			}
		});

		Future<Integer> secondFuture = executor.submit(new Callable<Integer>() {
			@Override
			public Integer call() throws Exception {
				System.out.println("Starting first callable...");
				Thread.sleep(1000);
				System.out.println("Finished.");
				return 211;
			}
		});
		System.out.println("End of application");
		for (int i = 0; i < 10; i++) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println(i);
		}
		try {
			System.out.println("Result is: " + future.get());
			System.out.println("Result is: " + secondFuture.get());
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}


		executor.shutdown();
	} 
}
