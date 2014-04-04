package javautilconcurent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExecutorsScheduledThreadPoolExecutor {
	public static void main(String[] args) {
		
		ScheduledExecutorService scheduledPool = Executors.newScheduledThreadPool(4);
		
		Thread t1 = new Thread(new Runnable() {
			
			@Override
			public void run() {
				System.out.println("Start thread " + Thread.currentThread().getName() + "");
				try {
					Thread.sleep(400);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.out.println("End thread " + Thread.currentThread().getName() + "");
			}
		});
		
		Thread t2 = new Thread(new Runnable() {
			
			@Override
			public void run() {
				System.out.println("Start thread " + Thread.currentThread().getName() + "");
				try {
					Thread.sleep(400);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.out.println("End thread " + Thread.currentThread().getName() + "");
			}
		});
		
		
		// 
		scheduledPool.schedule(t1, 10, TimeUnit.SECONDS);
		scheduledPool.schedule(t2, 3, TimeUnit.SECONDS);
		
		scheduledPool.shutdown();
		
		/// shutdown VS shutdownNow
		/*In summary, you can think of it that way:

		- shutdown() will just tell the executor service that it can't accept new tasks, but
		the already submitted tasks continue to run
		- shutdownNow() will do the same AND will try to cancel the already submitted tasks
		by interrupting the relevant threads. Note that if your tasks ignore the interruption,
		shutdownNow will behave exactly the same way as shutdown.*/

	}
}
