package waitandnotify.consumerproducer;

public class Consumer implements Runnable{
	Producer producer;
	
	public Consumer(Producer producer) {
		this.producer = producer;
	}
	
	@Override
	public void run() {
		while (true) {
			String message = producer.getMessage();
			System.out.println("Get message " + message);
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
}