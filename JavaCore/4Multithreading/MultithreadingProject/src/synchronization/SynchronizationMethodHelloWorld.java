package synchronization;

public class SynchronizationMethodHelloWorld {
	public static void main(String[] args) {
		
		Speaker speaker = new Speaker();
		
		//Thread t1 = new Thread(new ThreadSpeaker(new Speaker()));
		//Thread t2 = new Thread(new ThreadSpeaker(new Speaker()));
		/*			//OUTPUT: (No synchronization because Threads has different instances of Speacker class)
		0 Say: Thread-0
		0 Say: Thread-1
		1 Say: Thread-0
		1 Say: Thread-1
		2 Say: Thread-0
		2 Say: Thread-1
		0 Say: Thread-0
		0 Say: Thread-1
		1 Say: Thread-0
		1 Say: Thread-1
		2 Say: Thread-0
				*
		*/
		
		Thread t1 = new Thread(new ThreadSpeaker(speaker));
		Thread t2 = new Thread(new ThreadSpeaker(speaker));  // synchronized , because threads has one instance of Speaker class
		/*OUTPUT:
			0 Say: Thread-0
			1 Say: Thread-0
			2 Say: Thread-0
			0 Say: Thread-1
			1 Say: Thread-1
			2 Say: Thread-1
			0 Say: Thread-0
			1 Say: Thread-0
			2 Say: Thread-0
			0 Say: Thread-1
			1 Say: Thread-1
			2 Say: Thread-1*/
		
		
		t1.start();
		t2.start();
	}
}

class Speaker {
	public synchronized void say(String arg) {
		try {
			for (int i = 0; i < 3; i++){
				System.out.println(i + " Say: " + arg);
				Thread.sleep(14);
			}
		} catch (Exception e) {
		}
	}
}

class ThreadSpeaker implements Runnable{
	private Speaker speaker;
	
	public ThreadSpeaker(Speaker speaker){
		this.speaker = speaker;
	}
	
	@Override
	public void run() {
		try {
			for (int i = 0; i < 5; i++) {
				this.speaker.say(Thread.currentThread().getName());
				Thread.sleep(14);
			}
			
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}