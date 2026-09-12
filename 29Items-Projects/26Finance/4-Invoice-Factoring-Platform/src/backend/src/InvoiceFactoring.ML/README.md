# Credit-Risk Model (`InvoiceFactoring.ML`)

Predicts the **probability that a submitted invoice defaults** (the debtor fails to pay by
the due date + grace period). The probability drives the risk grade, advance rate, and
discount fee in `CreditAssessment` (see `Domain/Entities/CreditAssessment.cs`).

## Model
- **Algorithm:** LightGBM binary classifier (`Microsoft.ML.LightGbm`).
- **Target:** `Defaulted` (bool).
- **Output:** calibrated `Probability` of default ∈ [0, 1].

## Features
| Feature | Source |
| ------- | ------ |
| InvoiceAmount, PaymentTermDays, DaysUntilDue | Invoice itself |
| DebtorPriorInvoicesPaid / Defaulted | Historical debtor payment behaviour |
| BorrowerMonthlyInflow / Outflow / AvgDailyBalance / TenureMonths | **Plaid** cash-flow |
| IndustryCode | Company profile (one-hot encoded) |

> ⚠️ **Train/serve parity:** features must be computed identically here and in
> `AssessInvoiceCommandHandler`. Diverging logic is the #1 source of silent model error.

## Training
```bash
# CSV columns must match CreditRiskInput LoadColumn order, with a header row.
dotnet run --project tools/TrainerCli -- \
    --data ./data/invoice-outcomes.csv \
    --out  ./models/credit-risk-v1.zip
```
`CreditRiskTrainer` does an 80/20 split and reports **AUC-ROC, AUC-PR, F1**. Because
defaults are rare (class imbalance), **AUC-PR + calibration** are the metrics that matter —
not accuracy.

## Serving
The API/worker loads the `.zip` from Blob Storage via `Microsoft.Extensions.ML`
`PredictionEnginePool<CreditRiskInput, CreditRiskPrediction>` (thread-safe, pooled).
Infrastructure's `MlNetCreditScoringService` adapts the pool to the
`ICreditScoringService` port. Swap models by changing `ML:ModelPath` config — no redeploy.

**Default when no model artifact is present:** `HeuristicCreditScoringService` (a transparent
logistic scorer over the same features) is registered instead, so underwriting works out of
the box and produces real reason codes. DI selects the ML model automatically once the file
at `ML:ModelPath` exists — see `Infrastructure/DependencyInjection.cs`.

## Explainability & fairness (regulatory)
Lending decisions must be explainable. Capture **per-feature contributions** (ML.NET
`PermutationFeatureImportance` / `CalculateFeatureContribution`) at scoring time and persist
them as `CreditAssessment.ReasonCodesJson` to support **adverse-action notices** (US ECOA /
Reg B) and fair-lending audits.

## Monitoring (Phase 3)
- Input feature-distribution drift vs. training baseline.
- PD calibration (predicted vs. realized default rate) by cohort.
- Auto-trigger retraining when drift/calibration breaches thresholds.
