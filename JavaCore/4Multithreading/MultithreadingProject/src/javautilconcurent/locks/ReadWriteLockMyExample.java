package javautilconcurent.locks;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ReadWriteLockMyExample {
	public static void main(String[] args) {
		
		SyncObject obj = new SyncObject();
		
		Thread[] threads = new Thread[10];
		for (int i = 0; i < 10; i++) {
			threads[i] = new Thread(new MyTask(obj));
		}
		
		for (int i = 0; i < 10; i++) {
			threads[i].start();
		}
		// with locks (1) - execution of program takes 800 miliseconds
		// with locks (2) - execution of program takes 8000 miliseconds
	}
}


class SyncObject{
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	
	
	public void doWork() throws InterruptedException{
		//lock.readLock().lock();  // (1)
		lock.writeLock().lock();   // (2)
		System.out.println("Do important work");
		Thread.sleep(800);
		lock.writeLock().unlock();
		//lock.readLock().unlock();
	}
}

class MyTask implements Runnable{
	private SyncObject obj;
	public MyTask(SyncObject obj){
		this.obj = obj;
	}
	
	@Override
	public void run() {
		try {
			obj.doWork();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}			
	}
}