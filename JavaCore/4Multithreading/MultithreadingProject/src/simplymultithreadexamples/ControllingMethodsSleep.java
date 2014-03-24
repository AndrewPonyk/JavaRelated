package simplymultithreadexamples;

public class ControllingMethodsSleep {
	public static void main(String[] args) {
		System.out.println("using Thread.sleep method");
		Thread anim1 = new Thread(new Animations(140));
		anim1.start();
	}
}

class Animations implements Runnable {
	private int sleepTime;

	public Animations(int sleepTime) {
		this.sleepTime = sleepTime;
	}

	@Override
	public void run() {
		for (int i = 0; i < 5; i++) {
			try {
				System.out.println("i ..");
				Thread.sleep(this.sleepTime);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}