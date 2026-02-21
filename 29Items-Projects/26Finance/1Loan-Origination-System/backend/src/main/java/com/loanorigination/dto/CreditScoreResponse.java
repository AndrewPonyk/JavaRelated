package com.loanorigination.dto;

import lombok.Data;
import java.util.Map;

@Data
public class CreditScoreResponse {
    private Integer creditScore;
    private Double riskScore;
    private String riskCategory;
    private Map<String, Double> featureImportance;
}
