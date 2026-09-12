import React from 'react';
import { NlpAnalysisResult } from '../types/document';
import { Sparkles, Scale, DollarSign, FileText } from 'lucide-react';

interface SentimentBadgeProps {
  nlp?: NlpAnalysisResult;
}

export const SentimentBadge: React.FC<SentimentBadgeProps> = ({ nlp }) => {
  if (!nlp) {
    return <span className="badge" style={{ background: 'rgba(255,255,255,0.05)', color: 'var(--text-muted)' }}>Unprocessed</span>;
  }

  const getCategoryIcon = () => {
    switch (nlp.category) {
      case 'LEGAL_RISK':
        return <Scale size={13} />;
      case 'FINANCIAL':
        return <DollarSign size={13} />;
      default:
        return <FileText size={13} />;
    }
  };

  const isCritical = nlp.polarity === 'CRITICAL' || nlp.urgencyScore >= 0.75;
  const isPositive = nlp.polarity === 'POSITIVE';

  return (
    <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.4rem', flexWrap: 'wrap' }}>
      <span
        className="badge"
        style={{
          background: isCritical ? 'rgba(239, 68, 68, 0.15)' : 'rgba(99, 102, 241, 0.15)',
          color: isCritical ? '#f87171' : '#a5b4fc',
          border: `1px solid ${isCritical ? 'rgba(239, 68, 68, 0.3)' : 'rgba(99, 102, 241, 0.3)'}`,
        }}
      >
        {getCategoryIcon()}
        <span>{nlp.category.replace('_', ' ')}</span>
      </span>

      <span
        className="badge"
        title={`Sentiment Score: ${nlp.sentimentScore}`}
        style={{
          background: isCritical
            ? 'rgba(236, 72, 153, 0.15)'
            : isPositive
            ? 'rgba(16, 185, 129, 0.15)'
            : 'rgba(245, 158, 11, 0.15)',
          color: isCritical ? '#f472b6' : isPositive ? '#34d399' : '#fbbf24',
        }}
      >
        <Sparkles size={11} />
        <span>{nlp.polarity}</span>
      </span>
    </div>
  );
};
