package com.ap;

import javax.ejb.Schedule;
import javax.ejb.Stateless;
import javax.ejb.Timer;

@Stateless
public class MyEjbTimer {

    /**
     * Default constructor. 
     */
    public MyEjbTimer() {
        // TODO Auto-generated constructor stub
    }
	
	@Schedule(second="*/10", minute="*", hour="0-23", dayOfWeek="Mon-Sun",
      dayOfMonth="*", month="*", year="*", info="MyTimer", persistent=true)
    private void scheduledTimeout(final Timer t) {
        System.out.println("MyEjbTimer called at: " + new java.util.Date());
    }  
}