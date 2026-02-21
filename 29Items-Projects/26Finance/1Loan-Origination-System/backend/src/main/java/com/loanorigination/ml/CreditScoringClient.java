package com.loanorigination.ml;

import com.loanorigination.dto.CreditScoreRequest;
import com.loanorigination.dto.CreditScoreResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

@Component
@Slf4j
public class CreditScoringClient {

    private final RestTemplate restTemplate;
    private final String mlServiceUrl;

    public CreditScoringClient(
            RestTemplate restTemplate,
            @Value("${ml-service.url:http://localhost:8001}") String mlServiceUrl) {
        this.restTemplate = restTemplate;
        this.mlServiceUrl = mlServiceUrl;
    }

    public CreditScoreResponse getCreditScore(CreditScoreRequest request) {
        try {
            log.info("Calling ML service for credit score: {}", mlServiceUrl);
            String url = mlServiceUrl + "/api/score";
            
            CreditScoreResponse response = restTemplate.postForObject(
                url,
                request,
                CreditScoreResponse.class
            );

            if (response == null) {
                log.warn("ML service returned a null response body for url={}; using fallback scorer", url);
                return getFallbackScore(request);
            }

            log.info("Received credit score: {}", response.getCreditScore());
            return response;
            
        } catch (RestClientException e) {
            log.error("Error calling ML service: {}", e.getMessage());
            return getFallbackScore(request);
        }
    }

    private CreditScoreResponse getFallbackScore(CreditScoreRequest request) {
        log.warn("Using fallback credit scoring logic");
        
        CreditScoreResponse response = new CreditScoreResponse();
        
        int baseScore = 650;
        if (request.getAnnualIncome() >= 100000) {
            baseScore += 50;
        } else if (request.getAnnualIncome() < 30000) {
            baseScore -= 50;
        }
        
        double dti = request.getExistingDebt() / request.getAnnualIncome();
        if (dti > 0.5) {
            baseScore -= 80;
        } else if (dti < 0.3) {
            baseScore += 30;
        }
        
        if (request.getNumDelinquencies() != null) {
            baseScore -= request.getNumDelinquencies() * 30;
        }
        
        int creditScore = Math.max(300, Math.min(850, baseScore));
        double riskScore = 1.0 - ((creditScore - 300) / 550.0);
        
        response.setCreditScore(creditScore);
        response.setRiskScore(riskScore);
        response.setRiskCategory(riskScore < 0.3 ? "LOW" : riskScore < 0.6 ? "MEDIUM" : "HIGH");
        
        return response;
    }
}
