import play.*;
import play.jobs.*;
import play.test.*;
 
import models.*;
 
@OnApplicationStart
public class Bootstrap extends Job {
 
    public void doJob() {
    	if(User.count() == 0) {
            Fixtures.loadModels("initial-data.yml");
            System.out.println(User.count());
            for( Object u: User.all().fetch()){
            	System.out.println(u.toString());
            };
        }
    }
}