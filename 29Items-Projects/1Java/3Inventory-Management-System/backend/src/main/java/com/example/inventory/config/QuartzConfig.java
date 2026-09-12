package com.example.inventory.config;

import com.example.inventory.scheduling.ForecastRefreshJob;
import com.example.inventory.scheduling.InventoryReconciliationJob;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    @Bean
    JobDetail forecastRefreshJobDetail(
            @Value("${app.forecast.horizon-days:14}") int horizonDays) {
        return JobBuilder.newJob(ForecastRefreshJob.class)
                .withIdentity("forecast-refresh")
                .usingJobData("horizonDays", horizonDays)
                .requestRecovery()
                .storeDurably()
                .build();
    }

    @Bean
    Trigger forecastRefreshTrigger(JobDetail forecastRefreshJobDetail,
                                   @Value("${app.forecast.refresh-cron}") String cron) {
        return TriggerBuilder.newTrigger()
                .forJob(forecastRefreshJobDetail)
                .withIdentity("forecast-refresh-trigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                        .withMisfireHandlingInstructionDoNothing())
                .build();
    }

    @Bean
    JobDetail reconciliationJobDetail() {
        return JobBuilder.newJob(InventoryReconciliationJob.class)
                .withIdentity("inventory-reconciliation")
                .requestRecovery()
                .storeDurably()
                .build();
    }

    @Bean
    Trigger reconciliationTrigger(JobDetail reconciliationJobDetail,
                                  @Value("${app.reconciliation.cron:0 30 2 * * ?}") String cron) {
        return TriggerBuilder.newTrigger()
                .forJob(reconciliationJobDetail)
                .withIdentity("inventory-reconciliation-trigger")
                .withSchedule(CronScheduleBuilder.cronSchedule(cron)
                        .withMisfireHandlingInstructionDoNothing())
                .build();
    }

    @Bean
    SchedulerFactoryBeanCustomizer autowiringJobFactory(AutowireCapableBeanFactory beanFactory) {
        return scheduler -> scheduler.setJobFactory(new SpringBeanJobFactory() {
            @Override
            protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
                Object job = super.createJobInstance(bundle);
                beanFactory.autowireBean(job);
                return job;
            }
        });
    }
}
