package Bootstrap;

import models.Item;
import play.db.jpa.JPA;
import play.jobs.Job;
import play.test.Fixtures;

@play.jobs.OnApplicationStart
public class OnApplicationStart  extends Job{

	@Override
	public void doJob() throws Exception {
		System.out.println("APPLICATION STARTS");
	

		
		//create two items and save them in db
		Item i1=new Item();
		i1.name="item1";
		i1.count=10L;
		
		Item i2=new Item();
		i2.name="item2";
		i2.count=4L;
		
		Item i3=new Item();
		i3.name="Product3";
		i3.count=2L;
		
		Item i4=new Item();
		i4.name="Product4";
		i4.count=55L;
		
		
		
		i1.save();
		i2.save();
		i3.save();
		i4.save();
	
	}

}
