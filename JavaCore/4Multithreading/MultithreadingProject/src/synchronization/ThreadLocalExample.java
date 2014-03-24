package synchronization;

import java.util.Random;

public class ThreadLocalExample implements Runnable {

	// !!!!
	private static final ThreadLocal<String> threadLocalValue = new ThreadLocal<String>() {
		protected String initialValue() { //
			return "default value"; //
		}; // OPTIONAL BLOCK to set default value of ThreadLocal Object
	};

	@Override
	public void run() {
		System.out.println("Thread Name= " + Thread.currentThread().getName()
				+ " default Formatter = " + threadLocalValue.get());
		int nextValue = new Random().nextInt(1000);
		try {
			Thread.sleep(nextValue);
		} catch (InterruptedException e) {
		}

		threadLocalValue.set(nextValue + "");
		System.out.println("Thread Name= " + Thread.currentThread().getName()
				+ " default Formatter = " + threadLocalValue.get());
	}

	public static void main(String[] args) throws InterruptedException {
		ThreadLocalExample example = new ThreadLocalExample();
		for (int i = 0; i < 10; i++) {
			Thread t = new Thread(example, i + "");
			Thread.sleep(new Random().nextInt(1000));
			t.start();
		}
		Thread.sleep(9000);
		ThreadLocalExample.threadLocalValue
				.set("hello , i am thread local in main");
		System.out.println("In main Thread : "
				+ ThreadLocalExample.threadLocalValue.get());//
	}

}
