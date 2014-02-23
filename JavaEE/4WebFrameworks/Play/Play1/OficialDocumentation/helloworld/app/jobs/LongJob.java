package jobs;

import play.jobs.Job;

public class LongJob  extends Job<Void> {
    @Override
    public void doJob() throws Exception {
    	Thread.sleep(2000);
    }
}