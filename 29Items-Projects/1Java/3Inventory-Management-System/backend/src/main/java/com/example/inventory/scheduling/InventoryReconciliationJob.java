package com.example.inventory.scheduling;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class InventoryReconciliationJob implements Job {

    @Autowired
    private InventoryReconciliationService service;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            service.reconcile();
        } catch (RuntimeException exception) {
            throw new JobExecutionException("Inventory reconciliation failed", exception, true);
        }
    }
}

