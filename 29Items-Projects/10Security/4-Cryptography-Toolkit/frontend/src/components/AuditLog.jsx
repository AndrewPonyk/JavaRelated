import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { apiFetch, auth } from "../api/client.js";
import { useAsync } from "../hooks/useAsync.js";
import { ErrorText, Loading, Panel } from "./ui.jsx";

/** Admin-only audit trail. Sensitive params are already scrubbed server-side. */
export default function AuditLog() {
  const entries = useAsync(
    () =>
      auth.getToken()
        ? apiFetch("/auth/audit?limit=100", { auth: true })
        : Promise.resolve(null),
    [],
  );
  const [filter, setFilter] = useState("");
  const [me, setMe] = useState(null);

  useEffect(() => {
    if (!auth.getToken()) return;
    apiFetch("/auth/whoami", { auth: true })
      .then(setMe)
      .catch(() => setMe(null));
  }, []);

  if (!auth.getToken()) {
    return (
      <Panel title="Audit log — admin only">
        <p className="hint">
          <Link to="/login">Sign in</Link> with the admin account (the first one
          registered) to view the audit trail. Every crypto operation is
          recorded with scrubbed parameters — plaintexts, keys and passwords are
          never written to the log, and IPs are stored only as salted hashes
          (which stay in the database and are not exposed by the API).
        </p>
      </Panel>
    );
  }

  const all = entries.data?.items ?? [];
  const rows = filter
    ? all.filter((e) => e.op.toLowerCase().includes(filter.toLowerCase()))
    : all;
  const total = entries.data?.total;

  return (
    <Panel
      title="Audit log"
      status={entries.status}
      subtitle={
        me
          ? `Signed in as ${me.email}${me.is_admin ? " (admin)" : ""}`
          : undefined
      }
    >
      <div className="inline-row">
        <input
          placeholder="Filter by operation (e.g. aes, rsa, login)…"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          aria-label="Filter operations"
        />
      </div>
      {entries.status === "loading" && <Loading />}
      <ErrorText error={entries.error} />
      {/admin role required/i.test(entries.error ?? "") && (
        <p className="hint">
          This account is not an admin — only the first registered user gets the
          admin role.
        </p>
      )}
      <div className="table-wrap">
        <table className="audit-table">
          <thead>
            <tr>
              <th>#</th>
              <th>time (UTC)</th>
              <th>operation</th>
              <th>user</th>
              <th>params (scrubbed)</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((e) => (
              <tr key={e.id}>
                <td>{e.id}</td>
                <td>{e.created_at?.replace("T", " ").slice(0, 19) ?? "—"}</td>
                <td>
                  <code>{e.op}</code>
                </td>
                <td>{e.user_id ?? "—"}</td>
                <td>
                  <code>{JSON.stringify(e.params)}</code>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="hint">
        {rows.length} of {total ?? "…"} entries (newest first).
      </p>
    </Panel>
  );
}
