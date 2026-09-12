import { useState } from "react";
import { apiFetch } from "../api/client.js";
import { ErrorText, Field, KeyValue, Loading, Panel } from "./ui.jsx";

const PRESETS = ["interactive", "moderate", "paranoid"];

/** Argon2 panel: hash at a preset, feel the cost difference, verify back. */
export default function PasswordPanel() {
  const [password, setPassword] = useState("correct horse battery staple");
  const [preset, setPreset] = useState("interactive");
  const [hash, setHash] = useState(null);
  const [result, setResult] = useState(null);
  const [status, setStatus] = useState("idle");
  const [error, setError] = useState(null);

  async function run(fn) {
    setStatus("loading");
    setError(null);
    try {
      setResult(await fn());
      setStatus("ok");
    } catch (err) {
      setError(err.message);
      setStatus("error");
    }
  }

  return (
    <Panel
      title="Argon2id password hashing"
      status={status}
      subtitle="Presets pin (time, memory, parallelism): interactive ~50ms, moderate, paranoid (rate-limited!)."
    >
      <Field label="Password">
        <input
          type="text"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          data-testid="argon2-password"
        />
      </Field>
      <div className="inline-row">
        <select
          value={preset}
          onChange={(e) => setPreset(e.target.value)}
          aria-label="Preset"
        >
          {PRESETS.map((p) => (
            <option key={p}>{p}</option>
          ))}
        </select>
        <button
          disabled={status === "loading"}
          data-testid="argon2-hash"
          onClick={() =>
            run(async () => {
              const res = await apiFetch("/argon2/hash", {
                method: "POST",
                body: { password, preset },
              });
              setHash(res.hash);
              return { preset: res.preset, hash: res.hash };
            })
          }
        >
          Hash (feel the cost)
        </button>
        {hash && (
          <button
            disabled={status === "loading"}
            onClick={() =>
              run(() =>
                apiFetch("/argon2/verify", {
                  method: "POST",
                  body: { hash_string: hash, password },
                }),
              )
            }
          >
            Verify
          </button>
        )}
        {hash && (
          <button
            className="danger"
            disabled={status === "loading"}
            onClick={() =>
              run(async () => {
                try {
                  const ok = await apiFetch("/argon2/verify", {
                    method: "POST",
                    body: { hash_string: hash, password: password + "x" },
                  });
                  return { wrong_password_accepted: ok.verified };
                } catch (err) {
                  return { wrong_password_rejected: err.message };
                }
              })
            }
          >
            Verify wrong password
          </button>
        )}
      </div>
      {status === "loading" && (
        <Loading label="Burning time and RAM on purpose…" />
      )}
      <ErrorText error={error} />
      {result && <KeyValue entries={Object.entries(result)} />}
    </Panel>
  );
}
