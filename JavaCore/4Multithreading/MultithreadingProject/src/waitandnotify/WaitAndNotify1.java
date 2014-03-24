package waitandnotify;

public class WaitAndNotify1 {
	public static void main(String[] args) throws InterruptedException {
		System.out.println("");

		ThreadB b = new ThreadB();
		b.start();

		synchronized (b) {
			try {
				System.out.println("Waiting for b to complete...");
				b.wait();
			} catch (InterruptedException e) {
				//e.printStackTrace();
			}
			System.out.println("Total is: " +b.total);

		}
	}
}

class ThreadB extends Thread {
	public int total;

	@Override
	public String toString() {
		return this.total + "";
	}

	@Override
	public void run() {
		synchronized (this) {
			for (int i = 0; i <= 1000; i++) {
				total += i;
				//System.out.println(total);
			}
			notify();
		}
	}
}