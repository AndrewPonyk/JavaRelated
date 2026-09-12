import React, { useState } from 'react';
import { EdgeStatusDashboard } from './components/EdgeStatusDashboard';
import { AssetsManager } from './components/AssetsManager';

function App() {
  const [activeTab, setActiveTab] = useState<'dashboard' | 'assets'>('dashboard');

  return (
    <div className="app-container">
      <header className="app-header">
        <h1>High-Performance CDN Control Plane</h1>
        <nav>
          <button 
            className={activeTab === 'dashboard' ? 'active' : ''} 
            onClick={() => setActiveTab('dashboard')}
          >
            Metrics Dashboard
          </button>
          <button 
            className={activeTab === 'assets' ? 'active' : ''} 
            onClick={() => setActiveTab('assets')}
          >
            Routing Rules
          </button>
        </nav>
      </header>
      
      <main className="app-main">
        {activeTab === 'dashboard' ? <EdgeStatusDashboard /> : <AssetsManager />}
      </main>
    </div>
  );
}

export default App;
