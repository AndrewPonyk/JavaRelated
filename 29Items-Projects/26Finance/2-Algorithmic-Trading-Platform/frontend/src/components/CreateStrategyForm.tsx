// Controlled, client-validated form for registering a new strategy.

import { useState } from 'react';

import type { CreateStrategyRequest } from '../types/strategy';

interface Props {
  onCreate: (body: CreateStrategyRequest) => Promise<unknown>;
}

interface FormState {
  name: string;
  klass: string;
  symbols: string;
  maxPositionQty: string;
  maxOrderNotional: string;
}

const INITIAL: FormState = {
  name: '',
  klass: 'strategy_engine.strategies.momentum.EmaCrossoverStrategy',
  symbols: 'AAPL',
  maxPositionQty: '1000',
  maxOrderNotional: '250000',
};

function validate(form: FormState): Record<string, string> {
  const errors: Record<string, string> = {};
  if (form.name.trim().length < 3) errors.name = 'Name must be at least 3 characters';
  if (!/^[A-Za-z0-9 _-]+$/.test(form.name.trim()) && form.name.trim())
    errors.name = 'Only letters, numbers, spaces, _ and -';
  if (!form.klass.trim()) errors.klass = 'Strategy class is required';
  if (!form.symbols.trim()) errors.symbols = 'At least one symbol is required';
  if (!(Number(form.maxPositionQty) > 0)) errors.maxPositionQty = 'Must be a positive number';
  if (!(Number(form.maxOrderNotional) > 0)) errors.maxOrderNotional = 'Must be a positive number';
  return errors;
}

export default function CreateStrategyForm({ onCreate }: Props) {
  const [form, setForm] = useState<FormState>(INITIAL);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const update = (field: keyof FormState) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [field]: e.target.value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitError(null);
    const validationErrors = validate(form);
    setErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) return;

    setSubmitting(true);
    try {
      await onCreate({
        name: form.name.trim(),
        class: form.klass.trim(),
        symbols: form.symbols.split(',').map((s) => s.trim().toUpperCase()).filter(Boolean),
        max_position_qty: Number(form.maxPositionQty),
        max_order_notional: Number(form.maxOrderNotional),
      });
      setForm(INITIAL);
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Failed to create strategy');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form className="card" onSubmit={handleSubmit} aria-label="create-strategy">
      <h3>New Strategy</h3>
      <div className="field">
        <label htmlFor="name">Name</label>
        <input id="name" value={form.name} onChange={update('name')} placeholder="EMA Crossover" />
        {errors.name && <span className="error-text">{errors.name}</span>}
      </div>
      <div className="field">
        <label htmlFor="klass">Strategy class</label>
        <input id="klass" value={form.klass} onChange={update('klass')} />
        {errors.klass && <span className="error-text">{errors.klass}</span>}
      </div>
      <div className="field">
        <label htmlFor="symbols">Symbols (comma-separated)</label>
        <input id="symbols" value={form.symbols} onChange={update('symbols')} placeholder="AAPL, MSFT" />
        {errors.symbols && <span className="error-text">{errors.symbols}</span>}
      </div>
      <div className="row">
        <div className="field">
          <label htmlFor="qty">Max position qty</label>
          <input id="qty" type="number" value={form.maxPositionQty} onChange={update('maxPositionQty')} />
          {errors.maxPositionQty && <span className="error-text">{errors.maxPositionQty}</span>}
        </div>
        <div className="field">
          <label htmlFor="notional">Max order notional</label>
          <input id="notional" type="number" value={form.maxOrderNotional} onChange={update('maxOrderNotional')} />
          {errors.maxOrderNotional && <span className="error-text">{errors.maxOrderNotional}</span>}
        </div>
      </div>
      {submitError && <div className="banner error">{submitError}</div>}
      <button type="submit" disabled={submitting}>
        {submitting ? 'Creating…' : 'Create strategy'}
      </button>
    </form>
  );
}
