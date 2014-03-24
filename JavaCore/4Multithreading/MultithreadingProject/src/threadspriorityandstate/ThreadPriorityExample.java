package threadspriorityandstate;

public class ThreadPriorityExample {
	public static void main(String[] args) {

		Thread t1 = new Thread(new MyT(), "t1");
		Thread t2 = new Thread(new MyT(), "t22222222222");

		//t1.setPriority(Thread.MIN_PRIORITY);
		t2.setPriority(Thread.MAX_PRIORITY);

		t1.start();
		t2.start();
		
	}
}

class MyT implements Runnable {
	public void run() {
		for (int i = 0; i < 1000; i++) {
			System.out.println(Thread.currentThread().getName());
		}
	};
}
