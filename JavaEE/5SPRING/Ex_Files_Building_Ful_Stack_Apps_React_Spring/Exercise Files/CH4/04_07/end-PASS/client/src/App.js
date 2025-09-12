import React from 'react';
import Contacts from './components/Contacts';
import './App.css';

function App() {
  return (
    <div className="container-fluid">
      <nav>
        <div className="nav-wrapper center-align">
          <a href="/" className="brand-logo">Contacts</a>
        </div>
      </nav>
      <div className="row">
        <Contacts />
      </div>
      <div style={{ marginTop: '20px' }}>
        <b>React state vs ref</b>
        <div style={{ marginTop: '10px' }}>
          <img 
            src="/images/image-state-ref.png" 
            alt="State vs Ref 1" 
            style={{ width: '100%', maxWidth: '800px', height: 'auto', display: 'block', margin: '0 auto' }} 
          />
        </div>
        <div style={{ display: 'flex', gap: '20px', marginTop: '20px', justifyContent: 'center' }}>
          <img 
            src="/images/state-timer.png" 
            alt="State vs Ref 2" 
            style={{ maxWidth: '45%', height: 'auto' }} 
          />
          <img 
            src="/images/refs-examples.png" 
            alt="State vs Ref 3" 
            style={{ maxWidth: '45%', height: 'auto' }} 
          />
        </div>
      </div>
    </div>
  );
}

export default App;
