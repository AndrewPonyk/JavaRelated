package bootstrappackage;

import play.data.binding.As;
import play.jobs.Job;
import play.test.Fixtures;

@play.jobs.OnApplicationStart
public class LoadUsers extends Job{

	@Override
	public void doJob() throws Exception {
		Fixtures.loadModels("users.yml");
	}
}
