import { useState, type FormEvent } from 'react';
import { getCustomerSession, setCustomerSession, type PolicySummary } from './api/client';
import { ClaimsPanel } from './components/ClaimsPanel';
import { PolicyList } from './components/PolicyList';

// Dev sign-in stand-in: customers identify with the customer ID issued by their
// broker. In production this whole block is replaced by OIDC (code + PKCE against
// Portal.Identity) and the ID comes from the token's customer_id claim.
function SignIn({ onSignedIn }: { onSignedIn: () => void }) {
  const [customerId, setCustomerId] = useState('');
  const [error, setError] = useState<string | null>(null);

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const guidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    if (!guidPattern.test(customerId.trim())) {
      setError('Enter the customer ID from your welcome letter (a GUID).');
      return;
    }
    setCustomerSession(customerId.trim());
    onSignedIn();
  };

  return (
    <section className="card narrow">
      <h2>Sign in</h2>
      <form onSubmit={submit} noValidate>
        <div className="form-row">
          <label htmlFor="customer-id">Customer ID</label>
          <input
            id="customer-id"
            value={customerId}
            onChange={(e) => setCustomerId(e.target.value)}
            placeholder="00000000-0000-0000-0000-000000000000"
          />
        </div>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <button type="submit">View my policies</button>
      </form>
    </section>
  );
}

export function App() {
  const [signedIn, setSignedIn] = useState(() => getCustomerSession() !== null);
  const [selected, setSelected] = useState<PolicySummary | null>(null);
  const [refreshToken, setRefreshToken] = useState(0);

  const signOut = () => {
    setCustomerSession(null);
    setSelected(null);
    setSignedIn(false);
  };

  return (
    <div className="shell">
      <header>
        <h1>My Insurance</h1>
        {signedIn && (
          <button className="link" onClick={signOut}>
            Sign out
          </button>
        )}
      </header>
      <main>
        {!signedIn ? (
          <SignIn
            onSignedIn={() => {
              setSignedIn(true);
              setRefreshToken((t) => t + 1);
            }}
          />
        ) : (
          <>
            <section className="card">
              <h2>Your policies</h2>
              <PolicyList
                selectedPolicyId={selected?.id ?? null}
                onSelect={setSelected}
                refreshToken={refreshToken}
              />
            </section>
            {selected && <ClaimsPanel policy={selected} />}
          </>
        )}
      </main>
      <footer>Questions? Call your broker or 1-800-555-0123.</footer>
    </div>
  );
}
