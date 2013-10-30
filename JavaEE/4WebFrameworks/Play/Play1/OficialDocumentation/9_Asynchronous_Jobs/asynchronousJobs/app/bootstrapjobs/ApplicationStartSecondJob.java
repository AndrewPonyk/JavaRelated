package bootstrapjobs;

import play.jobs.Job;
import play.jobs.OnApplicationStart;

@OnApplicationStart
public class ApplicationStartSecondJob extends  Job {

		@Override
		public void doJob() throws Exception {
			System.out.println("Application starting - ApplicationAStartSecondJob");
		}
}
