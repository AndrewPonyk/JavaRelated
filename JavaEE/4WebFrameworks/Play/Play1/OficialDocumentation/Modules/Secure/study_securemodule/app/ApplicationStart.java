import play.*;
import play.jobs.*;
import play.test.*;

import models.*;
 
@OnApplicationStart
public class ApplicationStart extends Job {
 
    public void doJob() {
    	System.out.println("Load data from initial-data.yml");
        // Check if the database is empty
        if(User.count() == 0) {
            Fixtures.loadModels("initial-data.yml");
        }
        
        System.out.println(User.count());
    }
 
}