import PnLBar from './components/PnLBar';
import PositionsTable from './components/PositionsTable';
import StrategyDashboard from './components/StrategyDashboard';
import { usePortfolio } from './hooks/usePortfolio';
import './styles.css';

export default function App() {
  const { positions, pnl, loading, error } = usePortfolio();

  return (
    <div className="app">
      <header className="app-header">
        <div>
          <h1>Algorithmic Trading Platform</h1>
          <p className="subtitle">Strategy control &amp; live monitoring</p>
        </div>
        <PnLBar pnl={pnl} />
      </header>

      <main className="app-main">
        <StrategyDashboard />
        <PositionsTable positions={positions} loading={loading} error={error} />
      </main>

      <footer className="app-footer">
        Paper / live trading platform · data refreshes every 2s
      </footer>
    </div>
  );
}
