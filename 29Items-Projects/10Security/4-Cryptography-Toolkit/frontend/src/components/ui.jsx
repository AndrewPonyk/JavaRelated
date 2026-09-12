import StatusBadge from "./StatusBadge.jsx";

/** Shared presentational primitives: identical states everywhere. */

export function Panel({ title, status, children, subtitle }) {
  return (
    <section className="card">
      <h2>
        {title} {status && <StatusBadge status={status} />}
      </h2>
      {subtitle && <p className="subtitle">{subtitle}</p>}
      {children}
    </section>
  );
}

export function ErrorText({ error }) {
  if (!error) return null;
  return (
    <p className="error" role="alert">
      {error}
    </p>
  );
}

export function Loading({ label = "Working…" }) {
  return (
    <p className="skeleton" aria-busy="true">
      {label}
    </p>
  );
}

export function Field({ label, error, children }) {
  return (
    <label>
      {label}
      {children}
      {error && (
        <span className="field-error" role="alert">
          {error}
        </span>
      )}
    </label>
  );
}

export function KeyValue({ entries, title }) {
  if (!entries?.length) return null;
  return (
    <dl className="result">
      {title && <dt className="result-title">{title}</dt>}
      {entries.map(([k, v], i) => (
        <div className="kv" key={i}>
          <dt>{k}</dt>
          <dd className="mono">{String(v)}</dd>
        </div>
      ))}
    </dl>
  );
}

export function Mono({ lines }) {
  if (!lines?.length) return null;
  return <pre className="mono-block">{lines.join("\n")}</pre>;
}
