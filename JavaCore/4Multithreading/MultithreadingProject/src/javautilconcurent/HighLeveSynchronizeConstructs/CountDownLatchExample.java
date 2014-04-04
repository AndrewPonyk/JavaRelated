package javautilconcurent.HighLeveSynchronizeConstructs;

import java.util.concurrent.CountDownLatch;

public class CountDownLatchExample {

	public static void main(String[] args) throws InterruptedException {

		CountDownLatch latch = new CountDownLatch(3);

		Waiter waiter = new Waiter(latch);
		Decrementer decrementer = new Decrementer(latch);

		new Thread(waiter).start();
		new Thread(decrementer).start();
		
		Thread.sleep(4000);
	}

}

class Waiter implements Runnable {
	CountDownLatch latch = null;

	public Waiter(CountDownLatch latch) {
		this.latch = latch;
	}

	@Override
	public void run() {
		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		System.out.println("Waiter releases");
	}
}

class Decrementer implements Runnable {
	CountDownLatch latch = null;

	public Decrementer(CountDownLatch latch) {
		this.latch = latch;
	}

	@Override
	public void run() {
		try {
			Thread.sleep(1000);
			System.out.println("1 second");
			this.latch.countDown();

			Thread.sleep(1000);
			System.out.println("2 seconds");
			this.latch.countDown();

			Thread.sleep(1000);
			System.out.println("3 seconds");
			this.latch.countDown();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}