package javautilconcurent.CallableAndRunnable;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class CallableExample {
	public static void main(String[] args) throws InterruptedException, ExecutionException {
		
		ExecutorService pool = Executors.newFixedThreadPool(3);
	
		ThreadPoolExecutor p;
		
		Integer result = pool.submit(new WordLengthCallable("hello")).get(); 
		
		System.out.println(result);
		
		pool.shutdown();
		System.out.println("end of main");
	}
}

class WordLengthCallable implements Callable<Integer> {
	private String word;

	public WordLengthCallable(String word) {
		this.word = word;
	}

	public Integer call() {
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return Integer.valueOf(word.length());
	}
}