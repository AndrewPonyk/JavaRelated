import type { Severity } from '../types';
import { SEVERITY_COLOR, SEVERITY_LABEL } from '../lib/severity';

export function SeverityBanner({ severity }: { severity: Severity }) {
  return (
    <div className="severity-banner" style={{ borderColor: SEVERITY_COLOR[severity] }}>
      Highest severity:{' '}
      <strong style={{ color: SEVERITY_COLOR[severity] }}>{SEVERITY_LABEL[severity]}</strong>
    </div>
  );
}
