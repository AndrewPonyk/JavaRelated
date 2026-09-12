import { useCompanyInvoices, useAcceptOffer } from '@/hooks/useInvoices';
import { formatDate, formatMoney, formatPercent } from '@/lib/format';
import type { Invoice } from '@/types/invoice';

interface InvoiceListProps {
  companyId: string;
}

/**
 * Lists a company's invoices and their underwriting offers. Demonstrates the standard
 * data-fetching states: loading, error (with retry), empty, and populated.
 */
export function InvoiceList({ companyId }: InvoiceListProps) {
  const { data: invoices, isLoading, isError, error, refetch, isFetching } =
    useCompanyInvoices(companyId);

  if (isLoading) {
    return <p className="state">Loading invoices…</p>;
  }

  if (isError) {
    return (
      <div className="state state--error" role="alert">
        <p>Couldn’t load invoices: {(error as Error).message}</p>
        <button onClick={() => void refetch()}>Retry</button>
      </div>
    );
  }

  if (!invoices || invoices.length === 0) {
    return <p className="state">No invoices yet — submit one above to get started.</p>;
  }

  return (
    <section aria-busy={isFetching}>
      <table className="invoice-table">
        <thead>
          <tr>
            <th>Debtor</th>
            <th>Face value</th>
            <th>Due</th>
            <th>Status</th>
            <th>Offer</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {invoices.map((invoice) => (
            <InvoiceRow key={invoice.id} invoice={invoice} companyId={companyId} />
          ))}
        </tbody>
      </table>
    </section>
  );
}

function InvoiceRow({ invoice, companyId }: { invoice: Invoice; companyId: string }) {
  const acceptOffer = useAcceptOffer(companyId);

  return (
    <tr>
      <td>{invoice.debtorName}</td>
      <td>{formatMoney(invoice.faceValue, invoice.currency)}</td>
      <td>{formatDate(invoice.dueDate)}</td>
      <td>
        <StatusBadge status={invoice.status} />
      </td>
      <td>
        {invoice.offer ? (
          <span title={`PD ${formatPercent(invoice.offer.probabilityOfDefault)}`}>
            Grade {invoice.offer.riskGrade} ·{' '}
            <strong>{formatMoney(invoice.offer.netDisbursement, invoice.currency)}</strong> now
          </span>
        ) : invoice.declineReason ? (
          <span className="muted">{invoice.declineReason}</span>
        ) : (
          <span className="muted">—</span>
        )}
      </td>
      <td>
        {invoice.status === 'Approved' && (
          <button
            disabled={acceptOffer.isPending}
            onClick={() =>
              acceptOffer.mutate({ invoiceId: invoice.id, idempotencyKey: crypto.randomUUID() })
            }
          >
            {acceptOffer.isPending ? 'Processing…' : 'Accept advance'}
          </button>
        )}
      </td>
    </tr>
  );
}

function StatusBadge({ status }: { status: string }) {
  return <span className={`badge badge--${status.toLowerCase()}`}>{status}</span>;
}
