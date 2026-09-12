"""Synthetic customer data generator.

Used to seed the database for local/demo runs and to back tests, so the full
stack works end-to-end without a proprietary dataset. The generated data has a
deliberate, learnable churn signal (short tenure + many support tickets + high
charges raise churn probability).
"""

from __future__ import annotations

import numpy as np
import pandas as pd

CONTRACT_TYPES = ("Month-to-month", "One year", "Two year")
PAYMENT_METHODS = ("Electronic check", "Mailed check", "Bank transfer", "Credit card")


def generate_synthetic_customers(n: int = 1000, *, seed: int = 42) -> pd.DataFrame:
    """Return a schema-valid synthetic dataset with a learnable churn signal."""
    rng = np.random.default_rng(seed)

    tenure = rng.integers(0, 72, size=n)
    monthly = rng.uniform(20, 120, size=n)
    tickets = rng.poisson(2, size=n)
    contract = rng.choice(CONTRACT_TYPES, size=n, p=[0.55, 0.25, 0.20])
    payment = rng.choice(PAYMENT_METHODS, size=n)

    # Construct a churn signal: short tenure + many tickets + month-to-month +
    # electronic-check payment + high charges all push churn probability up.
    logit = (
        -1.8
        + 0.18 * tickets
        - 0.04 * tenure
        + 0.012 * monthly
        + 0.9 * (contract == "Month-to-month")
        + 0.5 * (payment == "Electronic check")
    )
    proba = 1.0 / (1.0 + np.exp(-logit))
    churn = rng.binomial(1, proba)

    return pd.DataFrame(
        {
            "customer_id": [f"CUST-{i:05d}" for i in range(n)],
            "tenure_months": tenure.astype(int),
            "monthly_charges": np.round(monthly, 2),
            "total_charges": np.round(monthly * np.maximum(tenure, 1), 2),
            "contract_type": contract,
            "payment_method": payment,
            "num_support_tickets": tickets.astype(int),
            "is_churned": churn.astype(int),
        }
    )
