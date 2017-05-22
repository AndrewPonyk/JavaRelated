package com.ap;

import org.apache.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Created by andrii on 20.05.17.
 */
public class TestJob implements Job {
    private Logger log = Logger.getLogger(TestJob.class);

    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        log.info("Test job executed");
        System.out.println(".");
    }
}
