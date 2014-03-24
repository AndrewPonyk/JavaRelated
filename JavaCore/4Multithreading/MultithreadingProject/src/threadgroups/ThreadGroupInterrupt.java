package threadgroups;

//InterruptThreadGroup.java
class InterruptThreadGroup {
	public static void main(String[] args) {
		ThreadGroup group = new ThreadGroup("A group");
		
		Thread mt = new Thread(group, new MyThread());
		mt.setName("A");
		mt.start();

		Thread mt2 = new Thread(group, new MyThread());
		mt2 = new MyThread();
		mt2.setName("B");
		mt2.start();
		try {
			Thread.sleep(2000); // Wait 2 seconds
		} catch (InterruptedException e) {
		}
		
		// Thread is Runnable =) !!!!!!!!!
		
		// Interrupt all methods in the same thread group as the main
		// thread
		Thread.currentThread().getThreadGroup().interrupt(); // interrupt all threads in group after 2 second
	}
	
}
class MyThread extends Thread {
	public void run() {
		
		for(int i=0;i<10000000;i++){
			if(i%148 == 0)
				System.out.println(Thread.currentThread().getName() + " , i = " + i);
			if(Thread.interrupted()){
				break;
			}
			Runnable d;
		}
	}
}