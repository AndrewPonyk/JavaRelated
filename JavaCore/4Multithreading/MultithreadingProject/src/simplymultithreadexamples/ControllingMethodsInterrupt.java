package simplymultithreadexamples;

public class ControllingMethodsInterrupt {
	public static void main(String[] args) throws InterruptedException {
		  Thread thrd = new Thread(new MyThread(), "MyThread #1");
		    Thread thrd2 = new Thread(new MyThread(), "MyThread #2");
		    
		    thrd.start();
		    Thread.sleep(1000);
		    thrd.interrupt();
	}
}

class MyThread implements Runnable {
	public void run() {
		System.out.println(Thread.currentThread().getName() + " starting.");
		try {

			for (int i = 1; i < 10000; i++) {
				if (Thread.interrupted()) {
					System.out.println("interrupted without exception, because current thread didnt sleep in MOMENT WHEN IT WAS 'intterrupted '");
					break;
				}
				System.out.print(".");
				for (long x = 0; x < 1000; x++){
					//Thread.sleep(70);
					System.out.println("/");
				}
			}
		} catch (Exception exc) {
			System.out.println(Thread.currentThread().getName()
					+ " interrupted. Exception");
		}
		System.out.println(Thread.currentThread().getName() + " exiting.");
	}
}