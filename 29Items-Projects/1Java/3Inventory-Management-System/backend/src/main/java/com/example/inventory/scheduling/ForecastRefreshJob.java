package com.example.inventory.scheduling;

import com.example.inventory.forecast.ForecastService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class ForecastRefreshJob implements Job {

    @Autowired
    private ForecastService forecastService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        int horizonDays = context.getMergedJobDataMap().getIntValue("horizonDays");
        try {
            forecastService.refreshAll(horizonDays);
        } catch (RuntimeException exception) {
            throw new JobExecutionException("Forecast refresh failed", exception, true);
        }
    }
}

