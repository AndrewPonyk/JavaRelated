package simplymultithreadexamples;

public class ControllingThreadsJoin {
	public static void main(String[] args) throws InterruptedException {
		
		
		
		Thread t1 = new Thread(new MyRunnable());
		Thread t2 = new Thread(new MyRunnable());
		
		t1.start();
		t1.join();   // main method(Thread)   wait until t1 finish it's work
		
		t2.start();
		t2.join();
		
		System.out.println("End of main");
	}
}

class MyRunnable implements Runnable {

	@Override
	public void run() {
		System.out.println("Thread started:::"
				+ Thread.currentThread().getName());
		try {
			Thread.sleep(4000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out
				.println("Thread ended:::" + Thread.currentThread().getName());
	}

}