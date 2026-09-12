import { useState } from "react";

import { ApiError, api } from "../api/client";
import type { PredictionResponse } from "../api/types";

const CATEGORIES = [
  "grocery",
  "electronics",
  "travel",
  "gambling",
  "jewelry",
  "restaurants",
  "fuel",
  "entertainment",
];

interface FieldErrors {
  transaction_id?: string;
  account_id?: string;
  amount?: string;
}

function localDatetimeNow(): string {
  const now = new Date();
  now.setSeconds(0, 0);
  const offset = now.getTimezoneOffset() * 60_000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 16);
}

/** Manual scoring form for exploratory testing of the live model. */
export default function PredictionForm() {
  const [transactionId, setTransactionId] = useState("");
  const [accountId, setAccountId] = useState("");
  const [amount, setAmount] = useState("100.00");
  const [category, setCategory] = useState(CATEGORIES[0]);
  const [timestamp, setTimestamp] = useState(localDatetimeNow);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<PredictionResponse | null>(null);

  function validate(): boolean {
    const next: FieldErrors = {};
    if (!transactionId.trim()) {
      next.transaction_id = "Transaction id is required.";
    }
    if (!accountId.trim()) {
      next.account_id = "Account id is required.";
    }
    const parsedAmount = Number(amount);
    if (!Number.isFinite(parsedAmount) || parsedAmount <= 0) {
      next.amount = "Amount must be a positive number.";
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  }

  function applyServerErrors(err: ApiError) {
    const next: FieldErrors = {};
    for (const issue of err.problem?.errors ?? []) {
      const field = issue.loc[issue.loc.length - 1];
      if (field === "transaction_id" || field === "account_id" || field === "amount") {
        next[field] = issue.msg;
      }
    }
    setErrors(next);
    setSubmitError(Object.keys(next).length > 0 ? null : err.detail);
  }

  async function submit() {
    if (!validate()) {
      return;
    }
    setSubmitting(true);
    setSubmitError(null);
    setResult(null);
    try {
      const response = await api.createPrediction({
        transaction_id: transactionId.trim(),
        account_id: accountId.trim(),
        amount: Number(amount),
        merchant_category: category,
        timestamp: new Date(timestamp).toISOString(),
      });
      setResult(response);
    } catch (err) {
      if (err instanceof ApiError) {
        applyServerErrors(err);
      } else {
        setSubmitError(String(err));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="card">
      <header className="card-header">
        <h2>Score a transaction</h2>
      </header>

      <form
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <div className="form-row">
          <label htmlFor="txn-id">Transaction id</label>
          <div className="input-with-button">
            <input
              id="txn-id"
              value={transactionId}
              onChange={(event) => setTransactionId(event.target.value)}
              placeholder="txn-0001"
            />
            <button
              type="button"
              onClick={() => setTransactionId(crypto.randomUUID())}
              title="Generate a random id"
            >
              ↻
            </button>
          </div>
        </div>
        {errors.transaction_id && <p className="field-error">{errors.transaction_id}</p>}

        <div className="form-row">
          <label htmlFor="acct-id">Account id</label>
          <input
            id="acct-id"
            value={accountId}
            onChange={(event) => setAccountId(event.target.value)}
            placeholder="acct-42"
          />
        </div>
        {errors.account_id && <p className="field-error">{errors.account_id}</p>}

        <div className="form-row">
          <label htmlFor="amount">Amount</label>
          <input
            id="amount"
            type="number"
            step="0.01"
            min="0.01"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
          />
        </div>
        {errors.amount && <p className="field-error">{errors.amount}</p>}

        <div className="form-row">
          <label htmlFor="category">Merchant category</label>
          <select
            id="category"
            value={category}
            onChange={(event) => setCategory(event.target.value)}
          >
            {CATEGORIES.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
        </div>

        <div className="form-row">
          <label htmlFor="timestamp">Timestamp</label>
          <input
            id="timestamp"
            type="datetime-local"
            value={timestamp}
            onChange={(event) => setTimestamp(event.target.value)}
          />
        </div>

        {submitError && <p className="error-banner">Scoring failed: {submitError}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? "Scoring…" : "Score"}
        </button>
      </form>

      {result && (
        <div className={`result-card ${result.is_fraud ? "fraud" : "legit"}`}>
          <strong>{result.is_fraud ? "FRAUD SUSPECTED" : "Looks legitimate"}</strong>
          <dl>
            <div>
              <dt>Probability</dt>
              <dd>{(result.fraud_probability * 100).toFixed(1)}%</dd>
            </div>
            <div>
              <dt>Variant</dt>
              <dd>{result.variant}</dd>
            </div>
            <div>
              <dt>Model</dt>
              <dd>v{result.model_version}</dd>
            </div>
            <div>
              <dt>Latency</dt>
              <dd>{result.latency_ms.toFixed(2)} ms</dd>
            </div>
          </dl>
        </div>
      )}
    </section>
  );
}
