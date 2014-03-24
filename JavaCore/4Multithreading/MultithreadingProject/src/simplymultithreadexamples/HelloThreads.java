package simplymultithreadexamples;

public class HelloThreads {
	public static void main(String[] args) {
		System.out.println("Hello threads");
		
		Thread animation1 = new Thread(new Animation());
		Thread animation2 = new Thread(new Animation());
		
		animation1.start();
		animation2.start();
		
	}
}


class Animation implements Runnable {
	@Override
	public void run() {
		for (int i = 0; i < 10; i++) {
			System.out.println("animation " + i);
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}