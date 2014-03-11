package innerclasses;

public class AnonymousInner {
	private int x = 10;

	public void printX() {
		final String s ;
		s = "1000";
		Thread t = new Thread() {
			public void run() {
				while (true) {
					System.out.println(s + x);
				}
			}
		};
		t.start();
	}

	public static void main(String[] args) {
		new AnonymousInner().printX();
	}
}