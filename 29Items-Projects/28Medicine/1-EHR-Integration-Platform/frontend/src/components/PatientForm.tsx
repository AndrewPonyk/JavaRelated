/**
 * PatientForm — register a patient with client-side validation that mirrors the
 * backend constraints. Demonstrates form validation + mutation + error/loading
 * states.
 */
import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { registerPatient } from '@/api/patientApi';
import type { PatientRegistration, PatientSummary } from '@/types/api';

const GENDERS = ['', 'male', 'female', 'other', 'unknown'];

const EMPTY: PatientRegistration = {
  identifierSystem: 'urn:mrn',
  identifierValue: '',
  familyName: '',
  givenName: '',
  birthDate: '',
  gender: '',
};

type Errors = Partial<Record<keyof PatientRegistration, string>>;

function validate(form: PatientRegistration): Errors {
  const errors: Errors = {};
  if (!form.identifierValue.trim()) errors.identifierValue = 'MRN is required';
  if (!form.familyName.trim()) errors.familyName = 'Family name is required';
  if (!form.givenName.trim()) errors.givenName = 'Given name is required';
  if (form.birthDate && new Date(form.birthDate) >= new Date()) {
    errors.birthDate = 'Birth date must be in the past';
  }
  return errors;
}

interface PatientFormProps {
  onCreated?: (summary: PatientSummary) => void;
}

export function PatientForm({ onCreated }: PatientFormProps): JSX.Element {
  const [form, setForm] = useState<PatientRegistration>(EMPTY);
  const [errors, setErrors] = useState<Errors>({});
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: registerPatient,
    onSuccess: (summary) => {
      setForm(EMPTY);
      setErrors({});
      void queryClient.invalidateQueries({ queryKey: ['patients'] });
      onCreated?.(summary);
    },
  });

  const update = (field: keyof PatientRegistration) => (value: string) =>
    setForm((prev) => ({ ...prev, [field]: value }));

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const found = validate(form);
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    // Omit empty optional fields so backend validators skip them.
    mutation.mutate({
      ...form,
      birthDate: form.birthDate || undefined,
      gender: form.gender || undefined,
    });
  }

  return (
    <form className="card form" onSubmit={handleSubmit} noValidate>
      <h2>Register patient</h2>

      <Field label="MRN / identifier" error={errors.identifierValue}>
        <input value={form.identifierValue} onChange={(e) => update('identifierValue')(e.target.value)} />
      </Field>
      <Field label="Identifier system">
        <input value={form.identifierSystem} onChange={(e) => update('identifierSystem')(e.target.value)} />
      </Field>
      <Field label="Family name" error={errors.familyName}>
        <input value={form.familyName} onChange={(e) => update('familyName')(e.target.value)} />
      </Field>
      <Field label="Given name" error={errors.givenName}>
        <input value={form.givenName} onChange={(e) => update('givenName')(e.target.value)} />
      </Field>
      <Field label="Date of birth" error={errors.birthDate}>
        <input type="date" value={form.birthDate} onChange={(e) => update('birthDate')(e.target.value)} />
      </Field>
      <Field label="Gender">
        <select value={form.gender} onChange={(e) => update('gender')(e.target.value)}>
          {GENDERS.map((g) => (
            <option key={g} value={g}>
              {g || '—'}
            </option>
          ))}
        </select>
      </Field>

      <button type="submit" disabled={mutation.isPending}>
        {mutation.isPending ? 'Saving…' : 'Register'}
      </button>

      {mutation.isError && (
        <p role="alert" className="error">
          {(mutation.error as Error).message}
        </p>
      )}
      {mutation.isSuccess && (
        <p role="status" className="success">
          Registered patient {mutation.data.id}
        </p>
      )}
    </form>
  );
}

function Field({
  label,
  error,
  children,
}: {
  label: string;
  error?: string;
  children: React.ReactNode;
}): JSX.Element {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {error && <span className="field__error">{error}</span>}
    </label>
  );
}
