interface AccountSummaryCardProps {
  label: string;
  value: string;
  /** Optional secondary line under the value. */
  hint?: string;
}

export function AccountSummaryCard({ label, value, hint }: AccountSummaryCardProps) {
  return (
    <article className="summary-card">
      <h3 className="summary-card__label">{label}</h3>
      <p className="summary-card__value">{value}</p>
      {hint && <p className="summary-card__hint">{hint}</p>}
    </article>
  );
}
