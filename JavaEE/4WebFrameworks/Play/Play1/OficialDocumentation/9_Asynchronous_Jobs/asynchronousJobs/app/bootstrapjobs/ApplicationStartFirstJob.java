package bootstrapjobs;

import play.jobs.Job;
import play.jobs.OnApplicationStart;


//! Interesting thing : <b> For example we have two classes with @OnApplicationStart ,
//then they are executed in ALPHABETICAL order</b>
@OnApplicationStart
public class ApplicationStartFirstJob  extends Job{
		
		public void doJob() throws Exception {
			System.out.println("Application starting - ApplicationStartFirstJob");
		}
	
}
