package com.loanorigination.drools;

import com.loanorigination.model.LoanApplication;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UnderwritingRulesService {

    private final KieContainer kieContainer;

    public UnderwritingRulesService(KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    public UnderwritingDecision executeRules(LoanApplication application) {
        log.info("Executing underwriting rules for application: {}", application.getApplicationId());

        KieSession kieSession = kieContainer.newKieSession();
        
        try {
            UnderwritingDecision decision = new UnderwritingDecision();
            decision.setApplicationId(application.getApplicationId());
            
            kieSession.insert(application);
            kieSession.insert(decision);
            
            int rulesFired = kieSession.fireAllRules();
            log.info("Fired {} rules for application: {}", rulesFired, application.getApplicationId());
            
            return decision;
            
        } finally {
            kieSession.dispose();
        }
    }

    @lombok.Data
    public static class UnderwritingDecision {
        private String applicationId;
        private String decision; // APPROVED, REJECTED, MANUAL_REVIEW
        private String reason;
        private java.util.List<String> conditions = new java.util.ArrayList<>();
    }
}
