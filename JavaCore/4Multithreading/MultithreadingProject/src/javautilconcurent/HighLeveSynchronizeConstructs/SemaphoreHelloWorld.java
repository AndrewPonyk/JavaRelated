package javautilconcurent.HighLeveSynchronizeConstructs;

import java.util.concurrent.Semaphore;

public class SemaphoreHelloWorld {
	private final Semaphore sem;
	
	public SemaphoreHelloWorld(Semaphore sem){
		this.sem = sem;
	}
	
	public void actionLimitBy3Threads(){
		try {
			sem.acquire();

			Thread.sleep(2000);
			System.out.println("Slept two second");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}finally{
			sem.release();
		}
		
	}
	
	public static void main(String[] args) {
		
		final SemaphoreHelloWorld hello = new SemaphoreHelloWorld(new Semaphore(3));
		
		for( int i = 0; i< 4; i++){
			new Thread(new Runnable() {
				
				@Override
				public void run() {
					hello.actionLimitBy3Threads();
				}
			}).start();;
		}
		
	}
	
	
	
}
