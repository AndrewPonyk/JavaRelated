package javautilconcurent.locks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class JournalDevLockExample {
	public static void main(String[] args) {
		

	}
}

class Resource {

	public void doSomething() {
		// do some operation, DB read, write etc
	}
	public void doLogging() {
		// logging, no need for thread safety
	}
}

class SynchronizedLockExample implements Runnable {

	private Resource resource;

	public SynchronizedLockExample(Resource r) {
		this.resource = r;
	}

	@Override
	public void run() {
		synchronized (resource) {
			resource.doSomething();
		}
		resource.doLogging();
	}
}
	/*As you can see that, I am using tryLock() method to make sure my thread waits only for definite time and if it’s
	not getting the lock on object, it’s just logging and exiting. Another important point to note is the use of
	try-finally block to make sure lock is released even if doSomething() method call throws any exception.*/

class ConcurrencyLockExample implements Runnable {

	private Resource resource;
	private Lock lock;

	public ConcurrencyLockExample(Resource r) {
		this.resource = r;
		this.lock = new ReentrantLock();
	}

	@Override
	public void run() {
		try {
			lock.tryLock(10, TimeUnit.SECONDS);
			resource.doSomething();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			lock.unlock();
		}
		resource.doLogging();
	}

}