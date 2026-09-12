import { useState } from 'react';
import { z } from 'zod';
import { useSubmitInvoice } from '@/hooks/useInvoices';

interface SubmitInvoiceFormProps {
  companyId: string;
}

const formSchema = z
  .object({
    debtorName: z.string().min(1, 'Debtor name is required'),
    debtorTaxId: z.string().min(1, 'Debtor tax id is required'),
    amount: z.coerce.number().positive('Amount must be greater than zero'),
    currency: z.string().length(3, 'Use a 3-letter code, e.g. USD'),
    issueDate: z.string().min(1, 'Issue date is required'),
    dueDate: z.string().min(1, 'Due date is required'),
  })
  .refine((d) => d.dueDate > d.issueDate, {
    message: 'Due date must be after the issue date',
    path: ['dueDate'],
  });

type FieldErrors = Partial<Record<keyof z.infer<typeof formSchema>, string>>;

const EMPTY = {
  debtorName: '',
  debtorTaxId: '',
  amount: '',
  currency: 'USD',
  issueDate: '',
  dueDate: '',
};

/** Controlled form with client-side zod validation; mirrors the server's FluentValidation rules. */
export function SubmitInvoiceForm({ companyId }: SubmitInvoiceFormProps) {
  const [values, setValues] = useState<Record<string, string>>(EMPTY);
  const [errors, setErrors] = useState<FieldErrors>({});
  const submit = useSubmitInvoice(companyId);

  const update = (field: string) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setValues((v) => ({ ...v, [field]: e.target.value }));

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const parsed = formSchema.safeParse(values);
    if (!parsed.success) {
      const fieldErrors: FieldErrors = {};
      for (const issue of parsed.error.issues) {
        fieldErrors[issue.path[0] as keyof FieldErrors] = issue.message;
      }
      setErrors(fieldErrors);
      return;
    }

    setErrors({});
    submit.mutate(
      { companyId, ...parsed.data, documentUri: null },
      { onSuccess: () => setValues(EMPTY) },
    );
  }

  return (
    <form onSubmit={handleSubmit} className="invoice-form" noValidate>
      <h2>Submit an invoice</h2>

      <Field label="Debtor name" error={errors.debtorName}>
        <input value={values.debtorName} onChange={update('debtorName')} />
      </Field>
      <Field label="Debtor tax id" error={errors.debtorTaxId}>
        <input value={values.debtorTaxId} onChange={update('debtorTaxId')} />
      </Field>
      <Field label="Amount" error={errors.amount}>
        <input type="number" step="0.01" value={values.amount} onChange={update('amount')} />
      </Field>
      <Field label="Currency" error={errors.currency}>
        <input maxLength={3} value={values.currency} onChange={update('currency')} />
      </Field>
      <Field label="Issue date" error={errors.issueDate}>
        <input type="date" value={values.issueDate} onChange={update('issueDate')} />
      </Field>
      <Field label="Due date" error={errors.dueDate}>
        <input type="date" value={values.dueDate} onChange={update('dueDate')} />
      </Field>

      <button type="submit" disabled={submit.isPending}>
        {submit.isPending ? 'Submitting…' : 'Submit for factoring'}
      </button>

      {submit.isError && (
        <p className="state state--error" role="alert">
          Submission failed: {(submit.error as Error).message}
        </p>
      )}
      {submit.isSuccess && <p className="state state--success">Invoice submitted — underwriting in progress.</p>}
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
}) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {error && <span className="field__error">{error}</span>}
    </label>
  );
}
