import {
  AlertTriangle,
  Download,
  Play,
  RefreshCw,
  RotateCw,
  Search,
  Trash2,
} from 'lucide-react';
import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';

import {
  createAnalysis,
  deleteAnalysis,
  getAnalysis,
  getSarifUrl,
  listAnalyses,
  listRules,
  rerunAnalysis,
} from '../api/client';
import type {
  Analysis,
  AnalysisListItem,
  AnalysisStatus,
  FindingSeverity,
  RuleDefinition,
} from '../types/analysis';

const sampleCode = `#include <stdlib.h>
#include <string.h>

int main(int argc, char** argv) {
  char* command = getenv("CMD");
  system(command);
  char* p = NULL;
  *p = 'x';
  if (argc > 0) {
    strcpy(command, argv[0]);
  }
  return 0;
  system("never");
}`;

export function AnalysisDashboard() {
  const [analyses, setAnalyses] = useState<AnalysisListItem[]>([]);
  const [selected, setSelected] = useState<Analysis | null>(null);
  const [rules, setRules] = useState<RuleDefinition[]>([]);
  const [projectName, setProjectName] = useState('demo-project');
  const [sourcePath, setSourcePath] = useState('sample.cpp');
  const [sourceCode, setSourceCode] = useState(sampleCode);
  const [statusFilter, setStatusFilter] = useState<AnalysisStatus | ''>('');
  const [severityFilter, setSeverityFilter] = useState<FindingSeverity | ''>('');
  const [findingFilter, setFindingFilter] = useState<FindingSeverity | ''>('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const severityCounts = useMemo(() => {
    const counts: Partial<Record<FindingSeverity, number>> = {};
    for (const finding of selected?.findings ?? []) {
      counts[finding.severity] = (counts[finding.severity] ?? 0) + 1;
    }
    return counts;
  }, [selected]);

  const visibleFindings = useMemo(() => {
    const findings = selected?.findings ?? [];
    return findingFilter ? findings.filter((finding) => finding.severity === findingFilter) : findings;
  }, [findingFilter, selected]);

  const refresh = useCallback(async (nextSelectedId?: string) => {
    setLoading(true);
    setError(null);
    try {
      const [items, loadedRules] = await Promise.all([
        listAnalyses({ status: statusFilter, severity: severityFilter }),
        listRules(),
      ]);
      setAnalyses(items);
      setRules(loadedRules);
      const selectedId = nextSelectedId ?? selected?.id ?? items[0]?.id;
      setSelected(selectedId ? await getAnalysis(selectedId) : null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to load analyzer data');
    } finally {
      setLoading(false);
    }
  }, [selected?.id, severityFilter, statusFilter]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!projectName.trim() || !sourcePath.trim()) {
      setError('Project and source path are required.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const created = await createAnalysis({
        project_name: projectName.trim(),
        source_path: sourcePath.trim(),
        source_code: sourceCode,
      });
      await refresh(created.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to create analysis');
    } finally {
      setSubmitting(false);
    }
  }

  async function openAnalysis(id: string) {
    setError(null);
    try {
      setSelected(await getAnalysis(id));
      setFindingFilter('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to open analysis');
    }
  }

  async function handleRerun() {
    if (!selected) return;
    setSubmitting(true);
    setError(null);
    try {
      const rerun = await rerunAnalysis(selected.id);
      await refresh(rerun.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to rerun analysis');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete() {
    if (!selected) return;
    setSubmitting(true);
    setError(null);
    try {
      await deleteAnalysis(selected.id);
      setSelected(null);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to delete analysis');
    } finally {
      setSubmitting(false);
    }
  }

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <h1>Static Analyzer</h1>
          <p>Code quality and security findings from AST facts, data flow, and Z3 checks.</p>
        </div>
        <button className="icon-button" type="button" onClick={() => refresh()} aria-label="Refresh">
          <RefreshCw size={18} />
        </button>
      </header>

      {error ? (
        <div className="error-state">
          <AlertTriangle size={18} />
          <span>{error}</span>
        </div>
      ) : null}

      <div className="workspace-grid">
        <section className="tool-panel">
          <form className="analysis-form" onSubmit={handleSubmit}>
            <label>
              Project
              <input value={projectName} onChange={(event) => setProjectName(event.target.value)} />
            </label>
            <label>
              Source path
              <input value={sourcePath} onChange={(event) => setSourcePath(event.target.value)} />
            </label>
            <label className="code-input">
              Source
              <textarea value={sourceCode} onChange={(event) => setSourceCode(event.target.value)} />
            </label>
            <button type="submit" disabled={submitting}>
              <Play size={16} />
              {submitting ? 'Running' : 'Run'}
            </button>
          </form>

          <div className="filters">
            <label>
              Status
              <select
                value={statusFilter}
                onChange={(event) => setStatusFilter(event.target.value as AnalysisStatus | '')}
              >
                <option value="">All</option>
                <option value="completed">Completed</option>
                <option value="failed">Failed</option>
                <option value="running">Running</option>
                <option value="queued">Queued</option>
              </select>
            </label>
            <label>
              Severity
              <select
                value={severityFilter}
                onChange={(event) => setSeverityFilter(event.target.value as FindingSeverity | '')}
              >
                <option value="">All</option>
                <option value="critical">Critical</option>
                <option value="high">High</option>
                <option value="medium">Medium</option>
                <option value="low">Low</option>
                <option value="info">Info</option>
              </select>
            </label>
          </div>

          <div className="analysis-list">
            {loading ? <div className="empty-state">Loading...</div> : null}
            {!loading && analyses.length === 0 ? <div className="empty-state">No analyses</div> : null}
            {analyses.map((analysis) => (
              <button
                className={`analysis-row ${selected?.id === analysis.id ? 'active' : ''}`}
                key={analysis.id}
                type="button"
                onClick={() => openAnalysis(analysis.id)}
              >
                <span>
                  <strong>{analysis.project_name}</strong>
                  <small>{analysis.source_path}</small>
                </span>
                <span className={`status status-${analysis.status}`}>{analysis.status}</span>
                <span className="count">{analysis.finding_count}</span>
              </button>
            ))}
          </div>
        </section>

        <section className="detail-panel">
          {selected ? (
            <>
              <div className="detail-header">
                <div>
                  <h2>{selected.project_name}</h2>
                  <p>{selected.source_path}</p>
                </div>
                <div className="actions">
                  <a className="icon-link" href={getSarifUrl(selected.id)} aria-label="Download SARIF">
                    <Download size={17} />
                  </a>
                  <button type="button" className="icon-button" onClick={handleRerun} aria-label="Rerun">
                    <RotateCw size={17} />
                  </button>
                  <button type="button" className="icon-button danger" onClick={handleDelete} aria-label="Delete">
                    <Trash2 size={17} />
                  </button>
                </div>
              </div>

              <div className="metric-grid">
                <Metric label="Findings" value={selected.findings.length.toString()} />
                <Metric label="Functions" value={(selected.ast_facts.functions?.length ?? 0).toString()} />
                <Metric label="Calls" value={(selected.ast_facts.calls?.length ?? 0).toString()} />
                <Metric label="Rules" value={rules.filter((rule) => rule.enabled).length.toString()} />
              </div>

              {selected.error_message ? (
                <div className="error-state">
                  <AlertTriangle size={18} />
                  <span>{selected.error_message}</span>
                </div>
              ) : null}

              <div className="severity-strip">
                {(['critical', 'high', 'medium', 'low', 'info'] as FindingSeverity[]).map((severity) => (
                  <button
                    key={severity}
                    type="button"
                    className={`severity-filter severity-${severity} ${
                      findingFilter === severity ? 'active' : ''
                    }`}
                    onClick={() => setFindingFilter(findingFilter === severity ? '' : severity)}
                  >
                    {severity}
                    <strong>{severityCounts[severity] ?? 0}</strong>
                  </button>
                ))}
              </div>

              <div className="finding-list">
                {visibleFindings.length === 0 ? (
                  <div className="empty-state">No matching findings</div>
                ) : (
                  visibleFindings.map((finding) => (
                    <article className="finding" key={finding.id}>
                      <div className="finding-title">
                        <span className={`severity-dot severity-${finding.severity}`} />
                        <strong>{finding.rule_id}</strong>
                        <small>
                          {finding.file_path}:{finding.line}:{finding.column}
                        </small>
                      </div>
                      <p>{finding.message}</p>
                    </article>
                  ))
                )}
              </div>
            </>
          ) : (
            <div className="empty-detail">
              <Search size={22} />
              <span>No analysis selected</span>
            </div>
          )}
        </section>
      </div>
    </main>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
