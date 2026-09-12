import { useState } from 'react';
import { PatientList } from '@/components/PatientList';
import { PatientDetail } from '@/components/PatientDetail';
import { PatientForm } from '@/components/PatientForm';
import { RiskPanel } from '@/components/RiskPanel';
import type { Patient } from '@/types/fhir';

type Tab = 'patients' | 'register' | 'risk';

const TABS: { id: Tab; label: string }[] = [
  { id: 'patients', label: 'Patients' },
  { id: 'register', label: 'Register' },
  { id: 'risk', label: 'Risk' },
];

export default function App(): JSX.Element {
  const [tab, setTab] = useState<Tab>('patients');
  const [selectedId, setSelectedId] = useState<string | null>(null);

  return (
    <main className="app">
      <header className="app__header">
        <div>
          <h1>EHR Integration Platform</h1>
          <p className="muted">Clinician console</p>
        </div>
        <nav className="tabs" aria-label="Sections">
          {TABS.map((t) => (
            <button
              key={t.id}
              type="button"
              className={tab === t.id ? 'tabs__tab tabs__tab--active' : 'tabs__tab'}
              aria-current={tab === t.id}
              onClick={() => setTab(t.id)}
            >
              {t.label}
            </button>
          ))}
        </nav>
      </header>

      {tab === 'patients' && (
        <div className="app__body">
          <PatientList onSelect={(p: Patient) => setSelectedId(p.id)} />
          <PatientDetail patientId={selectedId} />
        </div>
      )}

      {tab === 'register' && (
        <div className="app__body app__body--single">
          <PatientForm onCreated={() => setTab('patients')} />
        </div>
      )}

      {tab === 'risk' && (
        <div className="app__body app__body--single">
          <RiskPanel patientId={selectedId ?? ''} />
        </div>
      )}
    </main>
  );
}
