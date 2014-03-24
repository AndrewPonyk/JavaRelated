package waitandnotify.consumerproducer;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Producer implements Runnable{
	static final int MAXQUEUE = 5;
	private List<String> messages = new ArrayList<>();

	@Override
	public void run() {
		while (true) {
			putMessage();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	private synchronized void putMessage(){
		while (messages.size() >= MAXQUEUE) {
			try {
				wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		messages.add( (new Date()).toString() );
		notify();
	}
	
	public synchronized String getMessage() {
		
		try {
			while (messages.size() == 0) {
				wait();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		String message = messages.remove(0);
		notify();
		return message;
	}
}
