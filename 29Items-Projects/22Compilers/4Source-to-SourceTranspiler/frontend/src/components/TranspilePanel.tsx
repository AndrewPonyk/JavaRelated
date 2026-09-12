import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  CompileArtifact,
  CompileJob,
  CompileOptions,
  createCompileJob,
  deleteCompileJob,
  listCompileArtifacts,
  listCompileJobs,
  updateCompileJob
} from "../api/client";

const initialSource = `function add(a: number, b: number): number {
  return a + b;
}

const answer: number = identity!(40 + 2);
debug!(answer);`;

const defaultOptions: CompileOptions = {
  optimize: true,
  sourceMaps: true,
  target: "es2022"
};

export function TranspilePanel() {
  const [source, setSource] = useState(initialSource);
  const [options, setOptions] = useState<CompileOptions>(defaultOptions);
  const [jobs, setJobs] = useState<CompileJob[]>([]);
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null);
  const [artifacts, setArtifacts] = useState<CompileArtifact[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [artifactsLoading, setArtifactsLoading] = useState(false);

  const selectedJob = useMemo(
    () => jobs.find((job) => job.id === selectedJobId) ?? null,
    [jobs, selectedJobId]
  );
  const diagnostics = selectedJob?.output?.diagnostics ?? [];
  const hasSource = source.trim().length > 0;

  useEffect(() => {
    void refreshJobs();
  }, []);

  useEffect(() => {
    if (!selectedJobId) {
      setArtifacts([]);
      return;
    }

    setArtifactsLoading(true);
    listCompileArtifacts(selectedJobId)
      .then(setArtifacts)
      .catch((caught) => setError(errorMessage(caught)))
      .finally(() => setArtifactsLoading(false));
  }, [selectedJobId]);

  async function refreshJobs() {
    setLoading(true);
    setError(null);

    try {
      const nextJobs = await listCompileJobs();
      setJobs(nextJobs);
      if (!selectedJobId && nextJobs.length > 0) {
        selectJob(nextJobs[0]);
      }
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setLoading(false);
    }
  }

  function selectJob(job: CompileJob) {
    setSelectedJobId(job.id);
    setSource(job.source);
    setOptions(job.options);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!hasSource) {
      setError("source must not be empty");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const job = selectedJob
        ? await updateCompileJob(selectedJob.id, source, options)
        : await createCompileJob(source, options);
      const nextJobs = [job, ...jobs.filter((item) => item.id !== job.id)];
      setJobs(nextJobs);
      selectJob(job);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setLoading(false);
    }
  }

  async function handleCreateNew() {
    setSelectedJobId(null);
    setSource(initialSource);
    setOptions(defaultOptions);
    setArtifacts([]);
    setError(null);
  }

  async function handleDelete() {
    if (!selectedJob) {
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await deleteCompileJob(selectedJob.id);
      const remaining = jobs.filter((job) => job.id !== selectedJob.id);
      setJobs(remaining);
      if (remaining[0]) {
        selectJob(remaining[0]);
      } else {
        setSelectedJobId(null);
        setArtifacts([]);
      }
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="app-shell">
      <section className="workspace">
        <div className="toolbar">
          <div>
            <h1>Source-to-Source Transpiler</h1>
            <p>{selectedJob ? `Editing ${shortId(selectedJob.id)}` : "New compile job"}</p>
          </div>
          <div className="toolbar-actions">
            <button type="button" onClick={handleCreateNew}>
              New
            </button>
            <button type="button" onClick={refreshJobs} disabled={loading}>
              Refresh
            </button>
            <button type="button" onClick={handleDelete} disabled={!selectedJob || loading}>
              Delete
            </button>
          </div>
        </div>

        <form className="compile-form" onSubmit={handleSubmit}>
          <div className="options-row">
            <label>
              Target
              <select
                value={options.target}
                onChange={(event) =>
                  setOptions({ ...options, target: event.target.value as CompileOptions["target"] })
                }
              >
                <option value="es2020">ES2020</option>
                <option value="es2021">ES2021</option>
                <option value="es2022">ES2022</option>
                <option value="esnext">ESNext</option>
              </select>
            </label>
            <label className="check-control">
              <input
                type="checkbox"
                checked={options.optimize}
                onChange={(event) => setOptions({ ...options, optimize: event.target.checked })}
              />
              Optimize
            </label>
            <label className="check-control">
              <input
                type="checkbox"
                checked={options.sourceMaps}
                onChange={(event) => setOptions({ ...options, sourceMaps: event.target.checked })}
              />
              Source maps
            </label>
          </div>

          <label htmlFor="source">Source</label>
          <textarea
            id="source"
            value={source}
            onChange={(event) => setSource(event.target.value)}
            rows={16}
            spellCheck={false}
          />

          <button disabled={loading || !hasSource} type="submit">
            {loading ? "Running..." : selectedJob ? "Update and Compile" : "Create and Compile"}
          </button>
        </form>

        {error && <p className="error" role="alert">{error}</p>}
      </section>

      <aside className="side-panel">
        <section>
          <h2>Compile Jobs</h2>
          {jobs.length === 0 ? (
            <p className="muted">No compile jobs yet.</p>
          ) : (
            <ul className="job-list">
              {jobs.map((job) => (
                <li key={job.id}>
                  <button
                    className={job.id === selectedJobId ? "job-button selected" : "job-button"}
                    type="button"
                    onClick={() => selectJob(job)}
                  >
                    <span>{shortId(job.id)}</span>
                    <span data-status={job.status}>{job.status}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section>
          <h2>Output</h2>
          {diagnostics.length > 0 ? (
            <ul className="diagnostics">
              {diagnostics.map((diagnostic) => (
                <li key={`${diagnostic.code}-${diagnostic.message}`}>
                  <strong>{diagnostic.code}</strong>
                  <span>{diagnostic.message}</span>
                </li>
              ))}
            </ul>
          ) : (
            <pre>{selectedJob?.output?.javascript || "Compile a job to see JavaScript output."}</pre>
          )}
        </section>

        <section>
          <h2>Artifacts</h2>
          {artifactsLoading ? (
            <p className="muted">Loading artifacts...</p>
          ) : artifacts.length === 0 ? (
            <p className="muted">No artifacts loaded.</p>
          ) : (
            <ul className="artifact-list">
              {artifacts.map((artifact) => (
                <li key={artifact.id}>
                  <span>{artifact.artifactType}</span>
                  <code>{shortId(artifact.id)}</code>
                </li>
              ))}
            </ul>
          )}
        </section>
      </aside>
    </main>
  );
}

function shortId(id: string): string {
  return id.slice(0, 8);
}

function errorMessage(caught: unknown): string {
  return caught instanceof Error ? caught.message : "unexpected application error";
}
