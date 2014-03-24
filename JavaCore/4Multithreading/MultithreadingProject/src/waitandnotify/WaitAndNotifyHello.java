package waitandnotify;

public class WaitAndNotifyHello {
	public static void main(String[] args) throws InterruptedException {
		Thread t1 = new Thread(new T());
		
		t1.start();
		
		Thread.sleep(1000);
		t1.wait();
		Thread.sleep(1000);
		t1.notify();
		Thread.sleep(1000);
		
		
	}
}

class T implements Runnable {
	@Override
	public void run() {
		try {
			for (int i = 0; i < 100; i++) {
				System.out.println("i = " + i);
				Thread.sleep(19);
			}
		} catch (Exception e) {
		}
	}
}
