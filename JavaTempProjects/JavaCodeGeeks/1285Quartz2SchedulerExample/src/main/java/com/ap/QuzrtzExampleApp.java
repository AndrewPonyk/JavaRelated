package com.ap;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

/**
 * Hello world!
 */
public class QuzrtzExampleApp {
    public static void main(String[] args) throws SchedulerException {
        JobDetail job = JobBuilder.newJob(TestJob.class)
                .withIdentity("job1")
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(10).repeatForever())
                .build();

        SchedulerFactory schFactory = new StdSchedulerFactory();

        Scheduler sch = schFactory.getScheduler();
        System.out.println("I had problem with this, on agile-asseets project." +
                "Is scheduler ready :" + sch.isInStandbyMode());
        sch.start();
        sch.scheduleJob(job, trigger);


        System.out.println("end of program");
        System.out.println("Scheduler name:" + sch.getSchedulerName());
    }
}
