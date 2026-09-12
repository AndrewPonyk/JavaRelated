import { useState } from 'react';
import { ApiError, createMetricDefinition } from '../api/client';

// Mirrors the API's CreateMetricRequest validation (Bean Validation on the server).
const KEY_PATTERN = /^[a-z0-9]+(\.[a-z0-9_-]+)*$/;

interface Props {
  onCreated: () => void;
}

/** Register a new metric definition — client-side validation + server field errors. */
export default function CreateMetricForm({ onCreated }: Props) {
  const [metricKey, setMetricKey] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [unit, setUnit] = useState('');
  const [description, setDescription] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const validate = (): boolean => {
    const errors: Record<string, string> = {};
    if (metricKey.length < 3 || metricKey.length > 100) {
      errors.metricKey = '3–100 characters';
    } else if (!KEY_PATTERN.test(metricKey)) {
      errors.metricKey = 'Dot-separated lowercase segments, e.g. orders.completed';
    }
    if (!displayName.trim()) {
      errors.displayName = 'Required';
    } else if (displayName.length > 120) {
      errors.displayName = 'Max 120 characters';
    }
    if (unit.length > 20) errors.unit = 'Max 20 characters';
    if (description.length > 500) errors.description = 'Max 500 characters';
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSuccess(null);
    setSubmitError(null);
    if (!validate()) return;
    setSubmitting(true);
    try {
      await createMetricDefinition({
        metricKey,
        displayName,
        unit: unit || null,
        description: description || null,
      });
      setSuccess(`Metric '${metricKey}' registered`);
      setMetricKey('');
      setDisplayName('');
      setUnit('');
      setDescription('');
      setFieldErrors({});
      onCreated();
    } catch (err: unknown) {
      if (err instanceof ApiError && err.errors) {
        setFieldErrors(err.errors); // server-side field validation, same shape
      }
      setSubmitError(err instanceof Error ? err.message : 'Request failed');
    } finally {
      setSubmitting(false);
    }
  };

  const field = (
    name: string,
    label: string,
    value: string,
    onChange: (v: string) => void,
    placeholder?: string,
  ) => (
    <label className="form-field">
      <span>{label}</span>
      <input
        name={name}
        value={value}
        placeholder={placeholder}
        aria-invalid={fieldErrors[name] ? true : undefined}
        onChange={(e) => onChange(e.target.value)}
      />
      {fieldErrors[name] && <span className="field-error">{fieldErrors[name]}</span>}
    </label>
  );

  return (
    <section className="panel" aria-label="Register metric">
      <h2>Register a metric</h2>
      <form onSubmit={submit} noValidate>
        {field('metricKey', 'Metric key', metricKey, setMetricKey, 'orders.completed')}
        {field('displayName', 'Display name', displayName, setDisplayName, 'Orders completed')}
        {field('unit', 'Unit (optional)', unit, setUnit, 'count / EUR / ms')}
        {field('description', 'Description (optional)', description, setDescription)}
        <button type="submit" className="primary" disabled={submitting}>
          {submitting ? 'Registering…' : 'Register metric'}
        </button>
        {submitError && (
          <div className="state error" role="alert">
            {submitError}
          </div>
        )}
        {success && (
          <div className="form-success" role="status">
            ✓ {success}
          </div>
        )}
      </form>
    </section>
  );
}
