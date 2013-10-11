import models.User;
import play.Play;
import play.jobs.Job;
import play.jobs.OnApplicationStart;
import play.mvc.Before;
import play.test.Fixtures;

@OnApplicationStart
public class Bootstrap  extends Job{
	public void doJob() throws Exception {
		
		if(User.count()==0){     
			Fixtures.loadModels("initial-data.yml");
			System.out.println(User.count());
			User firstUser=(User) User.findById(new Long(1));
			System.out.println(firstUser.fullname);
		}else{
			System.out.println("Application Already has data");
		}
		   
		System.out.println("Application start");
	}
	
}
