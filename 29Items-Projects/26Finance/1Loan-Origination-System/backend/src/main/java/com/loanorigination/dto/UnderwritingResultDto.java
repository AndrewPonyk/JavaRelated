package com.loanorigination.dto;

import lombok.Data;

@Data
public class UnderwritingResultDto {
    private Long applicationId;
    private String decision;
    private String reason;
    private Integer creditScore;
    private Double riskScore;
    private String[] conditions;
    private boolean automated;
    private java.time.LocalDateTime decisionDate;
}
