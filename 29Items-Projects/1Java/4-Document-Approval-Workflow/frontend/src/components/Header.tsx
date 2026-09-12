import React from 'react';
import { Role } from '../types/document';
import { ShieldCheck, Plus, User } from 'lucide-react';

interface HeaderProps {
  currentRole: Role;
  onRoleChange: (role: Role) => void;
  onOpenSubmit: () => void;
}

export const Header: React.FC<HeaderProps> = ({ currentRole, onRoleChange, onOpenSubmit }) => {
  const roles: Role[] = [
    'CREATOR',
    'TEAM_LEAD',
    'DEPARTMENT_HEAD',
    'LEGAL_COUNSEL',
    'FINANCE_CONTROLLER',
    'EXECUTIVE',
    'ADMIN',
  ];

  return (
    <header className="app-header">
      <div className="logo-container">
        <div className="logo-icon">
          <ShieldCheck size={22} color="#ffffff" />
        </div>
        <div>
          <div className="logo-text">Document Approval Engine</div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
            Vert.x Reactive Bus • spaCy NLP • Quartz SLA
          </div>
        </div>
      </div>

      <div className="header-actions">
        <div className="role-badge">
          <User size={16} color="var(--accent-secondary)" />
          <span>Active Persona:</span>
          <select
            className="role-select"
            value={currentRole}
            onChange={(e) => onRoleChange(e.target.value as Role)}
          >
            {roles.map((r) => (
              <option key={r} value={r}>
                {r.replace('_', ' ')}
              </option>
            ))}
          </select>
        </div>

        <button className="btn btn-primary" onClick={onOpenSubmit}>
          <Plus size={16} />
          <span>Submit Document</span>
        </button>
      </div>
    </header>
  );
};
