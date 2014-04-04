package javautilconcurent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class ExecutorsInvokeAll {
	public static void main(String[] args) throws InterruptedException, ExecutionException {
	
		
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		Set<Callable<String>> callables = new HashSet<Callable<String>>();
	
		callables.add(new Callable<String>() {
		    public String call() throws Exception {
		        return "Task 1";
		    }
		});
		
		callables.add(new Callable<String>() {
		    public String call() throws Exception {
		        return "Task 3";
		    }
		});
		
		List<Future<String>> futures = executorService.invokeAll(callables);
		
		for (Future<String> future : futures) {
			System.out.println(future.get());
		}
		
		executorService.shutdown();
		
		/*
		ExecutorService Shutdown
		When you are done using the ExecutorService you should shut it down,
		so the threads do not keep running.

		For instance, if your application is started via a main() method and your
		main thread exits your application, the application will keep running if you
		have an active ExexutorService in your application. The active threads inside
		this ExecutorService prevents the JVM from shutting down
		
		To terminate the threads inside the ExecutorService you call its shutdown() method.
		The ExecutorService will not shut down immediately, but it will no longer accept
		new tasks, and once all threads have finished current tasks, the ExecutorService
		shuts down. All tasks submitted to the ExecutorService before shutdown() is called,
		are executed.
		
		If you want to shut down the ExecutorService immediately, you can call the shutdownNow()
		method. This will attempt to stop all executing tasks right away, and skips all submitted
		but non-processed tasks. There are no guarantees given about the executing tasks.
		Perhaps they stop, perhaps the execute until the end. It is a best effort attempt.
		
		*/
		
	}
}
