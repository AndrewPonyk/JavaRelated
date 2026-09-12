import React, { useState, useEffect } from 'react';
import { SlaRecord } from '../types/document';
import { Clock, AlertOctagon } from 'lucide-react';

interface SlaTimerBadgeProps {
  sla?: SlaRecord;
  isTerminal: boolean;
}

export const SlaTimerBadge: React.FC<SlaTimerBadgeProps> = ({ sla, isTerminal }) => {
  const [timeLeft, setTimeLeft] = useState<string>('');
  const [isBreached, setIsBreached] = useState<boolean>(sla?.breached || false);

  useEffect(() => {
    if (!sla?.deadline || isTerminal) {
      setTimeLeft(isTerminal ? 'Completed' : 'N/A');
      return;
    }

    const calculate = () => {
      const deadlineTime = new Date(sla.deadline!).getTime();
      const now = new Date().getTime();
      const diff = deadlineTime - now;

      if (diff <= 0 || sla.breached) {
        setIsBreached(true);
        setTimeLeft('SLA Expired');
      } else {
        const hours = Math.floor(diff / (1000 * 60 * 60));
        const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
        setTimeLeft(`${hours}h ${minutes}m left`);
      }
    };

    calculate();
    const timer = setInterval(calculate, 30000);
    return () => clearInterval(timer);
  }, [sla, isTerminal]);

  if (!sla) {
    return <span style={{ color: 'var(--text-dim)', fontSize: '0.8rem' }}>No SLA</span>;
  }

  if (isBreached) {
    return (
      <span className="badge badge-breached">
        <AlertOctagon size={13} />
        <span>SLA BREACHED</span>
      </span>
    );
  }

  return (
    <span
      className="badge"
      style={{
        background: 'rgba(6, 182, 212, 0.12)',
        color: 'var(--accent-secondary)',
        border: '1px solid rgba(6, 182, 212, 0.3)',
      }}
    >
      <Clock size={13} />
      <span>{timeLeft}</span>
    </span>
  );
};
