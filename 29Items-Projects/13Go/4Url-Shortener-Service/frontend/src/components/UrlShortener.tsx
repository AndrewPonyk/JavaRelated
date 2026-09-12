import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  BarChart3,
  Copy,
  ExternalLink,
  Link2,
  Loader2,
  QrCode,
  Save,
  Search,
  Trash2
} from "lucide-react";
import {
  AnalyticsSummary,
  createShortURL,
  deleteShortURL,
  getAnalytics,
  getShortURL,
  listShortURLs,
  qrURL,
  updateShortURL,
  URLResponse
} from "../api/client";

export function UrlShortener() {
  const [originalUrl, setOriginalUrl] = useState("");
  const [title, setTitle] = useState("");
  const [customCode, setCustomCode] = useState("");
  const [lookupCode, setLookupCode] = useState("");
  const [editUrl, setEditUrl] = useState("");
  const [editTitle, setEditTitle] = useState("");
  const [adminKey, setAdminKey] = useState("");
  const [result, setResult] = useState<URLResponse | null>(null);
  const [items, setItems] = useState<URLResponse[]>([]);
  const [analytics, setAnalytics] = useState<AnalyticsSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [loading, setLoading] = useState<string | null>(null);

  useEffect(() => {
    void refreshList();
  }, []);

  const canSubmit = useMemo(() => {
    try {
      const parsed = new URL(originalUrl);
      return parsed.protocol === "http:" || parsed.protocol === "https:";
    } catch {
      return false;
    }
  }, [originalUrl]);

  async function refreshList() {
    setLoading("list");
    try {
      const response = await listShortURLs(20);
      setItems(response.items);
    } catch {
      setItems([]);
    } finally {
      setLoading(null);
    }
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading("create");
    setError(null);
    setNotice(null);

    try {
      const created = await createShortURL({
        originalUrl,
        customCode: customCode || undefined,
        title: title || undefined
      });
      setResult(created);
      setLookupCode(created.shortCode);
      setEditUrl(created.originalUrl);
      setEditTitle(created.title ?? "");
      setNotice("Short URL created.");
      await refreshList();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to create short URL");
    } finally {
      setLoading(null);
    }
  }

  async function handleLookup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await loadByCode(lookupCode);
  }

  async function loadByCode(code: string) {
    setLoading("lookup");
    setError(null);
    setNotice(null);

    try {
      const found = await getShortURL(code);
      setResult(found);
      setLookupCode(found.shortCode);
      setEditUrl(found.originalUrl);
      setEditTitle(found.title ?? "");
      setAnalytics(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to load short URL");
    } finally {
      setLoading(null);
    }
  }

  async function handleUpdate() {
    if (!result) {
      return;
    }

    setLoading("update");
    setError(null);
    setNotice(null);
    try {
      const updated = await updateShortURL(result.shortCode, {
        originalUrl: editUrl || undefined,
        title: editTitle || undefined
      }, adminKey);
      setResult(updated);
      setNotice("Short URL updated.");
      await refreshList();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to update short URL");
    } finally {
      setLoading(null);
    }
  }

  async function handleDelete() {
    if (!result) {
      return;
    }

    setLoading("delete");
    setError(null);
    setNotice(null);
    try {
      await deleteShortURL(result.shortCode, adminKey);
      setNotice("Short URL deleted.");
      setResult(null);
      setAnalytics(null);
      await refreshList();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to delete short URL");
    } finally {
      setLoading(null);
    }
  }

  async function handleAnalytics() {
    if (!result) {
      return;
    }

    setLoading("analytics");
    setError(null);
    try {
      setAnalytics(await getAnalytics(result.shortCode, adminKey));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unable to load analytics");
    } finally {
      setLoading(null);
    }
  }

  async function copyShortURL() {
    if (!result) {
      return;
    }
    await navigator.clipboard.writeText(result.shortUrl);
    setNotice("Copied to clipboard.");
  }

  return (
    <div className="shortener-grid">
      <form className="panel" onSubmit={handleCreate}>
        <h2>Create link</h2>
        <label>
          Destination URL
          <input
            required
            type="url"
            value={originalUrl}
            onChange={(event) => setOriginalUrl(event.target.value)}
          />
        </label>
        <label>
          Title
          <input value={title} maxLength={200} onChange={(event) => setTitle(event.target.value)} />
        </label>
        <label>
          Custom code
          <input
            value={customCode}
            pattern="[A-Za-z0-9_-]{3,64}"
            onChange={(event) => setCustomCode(event.target.value)}
          />
        </label>
        <button type="submit" disabled={loading === "create" || !canSubmit}>
          {loading === "create" ? <Loader2 className="spin" size={18} /> : <Link2 size={18} />}
          Shorten
        </button>
      </form>

      <form className="panel" onSubmit={handleLookup}>
        <h2>Inspect link</h2>
        <label>
          Short code
          <input
            value={lookupCode}
            pattern="[A-Za-z0-9_-]{3,64}"
            onChange={(event) => setLookupCode(event.target.value)}
          />
        </label>
        <button type="submit" disabled={loading === "lookup" || !lookupCode}>
          {loading === "lookup" ? <Loader2 className="spin" size={18} /> : <Search size={18} />}
          Load
        </button>
      </form>

      <section className="panel">
        <h2>Admin access</h2>
        <label>
          API key
          <input
            type="password"
            value={adminKey}
            autoComplete="off"
            onChange={(event) => setAdminKey(event.target.value)}
          />
        </label>
      </section>

      {error && <div className="notice error">{error}</div>}
      {notice && <div className="notice success">{notice}</div>}

      {result && (
        <section className="result-panel">
          <div>
            <p className="eyebrow">Short URL</p>
            <a href={result.shortUrl} target="_blank" rel="noreferrer">
              {result.shortUrl}
            </a>
          </div>
          <div className="result-actions">
            <button type="button" onClick={copyShortURL} aria-label="Copy short URL">
              <Copy size={18} />
              Copy
            </button>
            <a className="button-link" href={result.shortUrl} target="_blank" rel="noreferrer">
              <ExternalLink size={18} />
              Open
            </a>
            <button type="button" onClick={handleAnalytics} disabled={loading === "analytics"}>
              {loading === "analytics" ? <Loader2 className="spin" size={18} /> : <BarChart3 size={18} />}
              Analytics
            </button>
          </div>
          <div className="detail-grid">
            <label>
              Destination
              <input type="url" value={editUrl} onChange={(event) => setEditUrl(event.target.value)} />
            </label>
            <label>
              Title
              <input value={editTitle} maxLength={200} onChange={(event) => setEditTitle(event.target.value)} />
            </label>
          </div>
          <div className="result-actions">
            <button type="button" onClick={handleUpdate} disabled={loading === "update" || !editUrl}>
              {loading === "update" ? <Loader2 className="spin" size={18} /> : <Save size={18} />}
              Save
            </button>
            <button type="button" className="danger" onClick={handleDelete} disabled={loading === "delete"}>
              {loading === "delete" ? <Loader2 className="spin" size={18} /> : <Trash2 size={18} />}
              Delete
            </button>
          </div>
          <div className="qr-row">
            <div>
              <p className="eyebrow">QR Code</p>
              <QrCode size={22} />
            </div>
            <img src={qrURL(result.shortCode)} alt={`QR code for ${result.shortUrl}`} />
          </div>
          <dl>
            <div>
              <dt>Destination</dt>
              <dd>{result.originalUrl}</dd>
            </div>
            <div>
              <dt>Created</dt>
              <dd>{new Date(result.createdAt).toLocaleString()}</dd>
            </div>
          </dl>
        </section>
      )}

      {analytics && (
        <section className="result-panel">
          <p className="eyebrow">Analytics</p>
          <h2>{analytics.totalClicks} total clicks</h2>
          <AnalyticsMap title="Referrers" values={analytics.byReferrer} />
          <AnalyticsMap title="Countries" values={analytics.byCountry} />
          <AnalyticsMap title="User agents" values={analytics.byUserAgent} />
        </section>
      )}

      <section className="result-panel">
        <div className="list-heading">
          <h2>Recent links</h2>
          {loading === "list" && <Loader2 className="spin" size={18} />}
        </div>
        <div className="link-list">
          {items.map((item) => (
            <button type="button" className="link-row" key={item.id} onClick={() => void loadByCode(item.shortCode)}>
              <span>{item.shortCode}</span>
              <small>{item.originalUrl}</small>
            </button>
          ))}
          {items.length === 0 && <p className="muted">No links loaded.</p>}
        </div>
      </section>
    </div>
  );
}

function AnalyticsMap({ title, values }: { title: string; values: Record<string, number> }) {
  const entries = Object.entries(values ?? {});
  return (
    <div className="analytics-block">
      <h3>{title}</h3>
      {entries.length === 0 ? (
        <p className="muted">No data</p>
      ) : (
        entries.map(([key, count]) => (
          <div className="metric-row" key={key}>
            <span>{key}</span>
            <strong>{count}</strong>
          </div>
        ))
      )}
    </div>
  );
}
