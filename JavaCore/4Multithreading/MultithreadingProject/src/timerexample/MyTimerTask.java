package timerexample;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class MyTimerTask extends TimerTask {

	@Override
	public void run() {
		System.out.println("Timer task started at:" + new Date());
		completeTask();
		System.out.println("Timer task finished at:" + new Date());
	}

	private void completeTask() {
		try {
			// assuming it takes 2 secs to complete the task
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public static void main(String args[]) {
		TimerTask timerTask = new MyTimerTask();

		// running timer task as daemon thread
		Timer timer = new Timer(true);
		timer.scheduleAtFixedRate(timerTask, 0, 10 * 1000); // execute timer task every 10 seconds (as javascript setInterval)
		//timer.schedule(timerTask, 2000);  // execute once , with delay 2000 seconds ( as javascript setTimeout)
		
		
		System.out.println("TimerTask started");
		// cancel after sometime
		try {
			Thread.sleep(12000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		timer.cancel();
		System.out.println("TimerTask cancelled");

	}

}