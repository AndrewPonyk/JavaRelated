package waitandnotify.consumerproducer;

public class ConsumerProducerProgram {
	public static void main(String[] args) {
		
		Producer producer = new Producer();
		Consumer consumer = new Consumer(producer);
		
		new Thread(producer).start();
		new Thread(consumer).start();
		
		
	}
}
