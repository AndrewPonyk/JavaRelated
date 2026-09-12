import { InvoiceList } from '@/components/InvoiceList';
import { SubmitInvoiceForm } from '@/components/SubmitInvoiceForm';

// Demo company id. In the real app this comes from the authenticated user's profile
// (Azure AD B2C claim), not a hard-coded constant.
const DEMO_COMPANY_ID = '00000000-0000-0000-0000-000000000001';

export function App() {
  return (
    <main className="container">
      <header>
        <h1>Invoice Factoring</h1>
        <p className="muted">Turn unpaid invoices into working capital.</p>
      </header>

      <SubmitInvoiceForm companyId={DEMO_COMPANY_ID} />

      <h2>Your invoices</h2>
      <InvoiceList companyId={DEMO_COMPANY_ID} />
    </main>
  );
}
