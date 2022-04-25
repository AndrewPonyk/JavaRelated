package javautilconcurent.futuretaskclass;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;


///!!! Future is just interface. Behind the scene, the implementation is FutureTask.
/*
You can absolutely use FutureTask manually but you will lose the advantages of using Executor (pooling thread, limit the thread, etc).
 Using FutureTask is quite similar to using the old Thread and using the run method.
 */
public class FutureTaskClassHelloWorld {
	public static void main(String[] args) throws InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(1);

		final SlowStringReverser reverser = new SlowStringReverser();
		final String target = "hell";
		
		
		FutureTask<String> future = new FutureTask<String>(new Callable<String>() {
			@Override
			public String call() throws Exception {
				String result = reverser.reverseString(target);
				return result;
			}
		});
		
		executor.execute(future); // doesn't block main THREAD !!!!!!!!!!!!!!!!! !!!!
		executor.shutdown();

		while (!future.isDone()) {    // future.get() BLOCK main thread , and wait for result !
			System.out.println("Waiting..");
			// Play framework has 'await' method
			Thread.sleep(500);
		}

		try {
			System.out.println("Result " + future.get());
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		System.out.println("end of main");
	}
}

class SlowStringReverser {
	StringBuffer orgString;
	StringBuffer reversedString;

	SlowStringReverser(String orgStr) {
		orgString = new StringBuffer(orgStr);
	}

	SlowStringReverser() {
	}

	public String reverseString(String str) {
		orgString = new StringBuffer(str);
		reversedString = new StringBuffer();
		for (int i = (orgString.length() - 1); i >= 0; i--) {

			reversedString.append(orgString.charAt(i));
			try {
				Thread.sleep(1000);
				System.out.println("processing ...");
			} catch (InterruptedException ie) {
			}
		}
		return reversedString.toString();
	}

}