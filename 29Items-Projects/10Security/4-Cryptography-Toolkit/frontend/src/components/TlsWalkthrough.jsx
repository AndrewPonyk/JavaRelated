import { useState } from "react";
import { apiFetch } from "../api/client.js";
import { useAsync } from "../hooks/useAsync.js";
import { ErrorText, Loading, Mono, Panel } from "./ui.jsx";

const STEP_TITLES = {
  client_hello: "① ClientHello — key share in the first flight",
  server_hello: "② ServerHello — suite chosen, 1-RTT",
  derive_handshake_keys: "③ Key schedule — HKDF over the X25519 shared secret",
  server_certificates: "④ Encrypted server flight",
  server_finished: "⑤ Server Finished — transcript MAC",
  client_finished: "⑥ Client Finished → application secrets",
  application_data: "⑦ Application data — real AES-GCM record",
  resumption: "⑧ Resumption & 0-RTT (replay caveat)",
};

/** TLS 1.3 walkthrough: real crypto values, step-by-step, plus downgrade sim. */
export default function TlsWalkthrough() {
  const [suite, setSuite] = useState("TLS_AES_128_GCM_SHA256");
  const [open, setOpen] = useState({});
  const handshake = useAsync(
    () => apiFetch(`/tls13/handshake?suite=${encodeURIComponent(suite)}`),
    [suite],
  );
  const downgrade = useAsync(() => apiFetch("/tls13/downgrade"), []);

  const steps = handshake.data?.steps ?? [];

  return (
    <>
      <Panel
        title="TLS 1.3 handshake — byte by byte"
        status={handshake.status}
        subtitle="SIMULATION with real crypto: X25519, the RFC 8446 HKDF schedule, transcript MACs, AES-GCM records. For the live connection, open devtools — this site is served over TLS 1.3."
      >
        <div className="inline-row">
          <select
            value={suite}
            onChange={(e) => setSuite(e.target.value)}
            aria-label="Cipher suite"
          >
            <option>TLS_AES_128_GCM_SHA256</option>
            <option>TLS_AES_256_GCM_SHA384</option>
          </select>
          <button
            onClick={() =>
              handshake.run(() =>
                apiFetch(`/tls13/handshake?suite=${encodeURIComponent(suite)}`),
              )
            }
          >
            Re-run handshake
          </button>
        </div>

        {handshake.status === "loading" && (
          <Loading label="Running the key schedule…" />
        )}
        <ErrorText error={handshake.error} />

        <ol className="steps">
          {steps.map((step) => (
            <li key={step.idx}>
              <button
                className="step-toggle"
                aria-expanded={Boolean(open[step.idx])}
                onClick={() =>
                  setOpen((prev) => ({ ...prev, [step.idx]: !prev[step.idx] }))
                }
              >
                {STEP_TITLES[step.name] ?? step.name}
              </button>
              <p className="step-detail">{step.detail}</p>
              {open[step.idx] && (
                <Mono
                  lines={Object.entries(step)
                    .filter(
                      ([k]) =>
                        ![
                          "idx",
                          "name",
                          "detail",
                          "suites",
                          "plaintext_preview",
                        ].includes(k),
                    )
                    .map(([k, v]) =>
                      k === "downgrade_sentinel_bytes"
                        ? `${k}: ${v} ("DOWNGRD\\x01")`
                        : `${k}: ${v}`,
                    )}
                />
              )}
              {step.plaintext_preview && open[step.idx] && (
                <p className="hint">
                  Record decrypts to: “{step.plaintext_preview}”
                </p>
              )}
            </li>
          ))}
        </ol>
      </Panel>

      <Panel
        title="Downgrade / version-stripping attack — and why it fails"
        status={downgrade.status}
        subtitle="An on-path attacker deletes the TLS 1.3 extensions. Three defences bite."
      >
        {downgrade.status === "loading" && <Loading />}
        <ErrorText error={downgrade.error} />
        {downgrade.data && (
          <>
            <h3>Attack steps</h3>
            <ol className="steps">
              {downgrade.data.attack_steps.map((s, i) => (
                <li key={i}>{s}</li>
              ))}
            </ol>
            <h3>Defences</h3>
            <ul>
              {downgrade.data.defenses.map((d, i) => (
                <li key={i}>
                  <strong>{d.name}</strong> — {d.detail}
                  {d.server_random_with_sentinel && (
                    <Mono
                      lines={[
                        `ServerHello.random = ${d.server_random_with_sentinel}`,
                      ]}
                    />
                  )}
                </li>
              ))}
            </ul>
            <p className="hint">
              Countermeasure: {downgrade.data.countermeasure}
            </p>
          </>
        )}
      </Panel>
    </>
  );
}
