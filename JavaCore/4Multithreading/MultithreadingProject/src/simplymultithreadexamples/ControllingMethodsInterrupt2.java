package simplymultithreadexamples;



public class ControllingMethodsInterrupt2 {
	public static void main(String[] args) throws InterruptedException {
		Thread a1 = new Thread(new Action());
		a1.start();
		
		
		/*for(int i=0 ; i< 50000000;i++){
			int k = i;
			k = k *2;
			if(k%333 == 3){
				System.out.println("main is running");
			}
		}
		*/
		Thread.sleep(8000);
		
		
		a1.interrupt();
		
	}
}


class Action implements Runnable {
	@Override
	public void run() {
		try {
			Thread.sleep(9000);
			
			for(int i =0; i<100000000;i++){
				if(i%43 == 0){
					System.out.println(i +" ;");
				}
				if(Thread.interrupted()){
					System.out.println("break , and no exception is thrown");
					break;
				}
			}
			
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}