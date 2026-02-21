package com.loanorigination.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreditScoreRequest {
    private Double annualIncome;
    private Double existingDebt;
    private Double loanAmount;
    private Double employmentYears;
    private Integer age;
    private Integer numPreviousLoans;
    private Integer numDelinquencies;
}
