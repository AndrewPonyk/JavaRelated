import { useState } from "react";
import { apiFetch } from "../api/client.js";
import { ErrorText, Field, KeyValue, Loading, Panel } from "./ui.jsx";

const TABS = [
  ["sign", "Sign / verify"],
  ["determinism", "Random vs RFC 6979 k"],
  ["k-reuse", "k-reuse key recovery (gallery)"],
];

/** ECDSA lab: standard signing, nonce behaviour, and the Sony-style leak. */
export default function EcdsaLab() {
  const [tab, setTab] = useState("sign");
  const [kp, setKp] = useState(null);
  const [message, setMessage] = useState("the document");
  const [message2, setMessage2] = useState("the forged document");
  const [result, setResult] = useState(null);
  const [status, setStatus] = useState("idle");
  const [error, setError] = useState(null);

  async function run(fn) {
    setStatus("loading");
    setError(null);
    setResult(null);
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
      title="ECDSA lab — one bad nonce leaks everything"
      status={kp ? status : "idle"}
      subtitle="P-256. The k-reuse tab reproduces the 2010 Sony PS3 key leak with two signatures."
    >
      {!kp ? (
        <>
          <p>Generate a P-256 demo keypair to start.</p>
          <div className="actions">
            <button
              disabled={status === "loading"}
              onClick={() =>
                run(async () =>
                  setKp(
                    await apiFetch("/ecdsa/keygen", {
                      method: "POST",
                      body: { curve: "P-256" },
                    }),
                  ),
                )
              }
            >
              Generate keypair
            </button>
          </div>
        </>
      ) : (
        <>
          <KeyValue
            title="Keypair"
            entries={[
              ["curve", kp.curve],
              ["public PEM", kp.public_pem.split("\n")[0] + "…"],
            ]}
          />
          <div className="tab-row" role="tablist">
            {TABS.map(([id, label]) => (
              <button
                key={id}
                role="tab"
                aria-selected={tab === id}
                className={tab === id ? "tab active" : "tab"}
                onClick={() => {
                  setTab(id);
                  setResult(null);
                  setError(null);
                }}
              >
                {label}
              </button>
            ))}
          </div>

          {tab !== "k-reuse" ? (
            <Field label="Message">
              <input
                value={message}
                onChange={(e) => setMessage(e.target.value)}
              />
            </Field>
          ) : (
            <>
              <Field label="Message 1">
                <input
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                />
              </Field>
              <Field label="Message 2">
                <input
                  value={message2}
                  onChange={(e) => setMessage2(e.target.value)}
                />
              </Field>
            </>
          )}

          {tab === "sign" && (
            <div className="actions">
              <button
                disabled={status === "loading"}
                onClick={() =>
                  run(async () => {
                    const sig = await apiFetch("/ecdsa/sign", {
                      method: "POST",
                      body: {
                        private_pem: kp.private_pem,
                        message,
                        mode: "deterministic-rfc6979",
                      },
                    });
                    const ok = await apiFetch("/ecdsa/verify", {
                      method: "POST",
                      body: {
                        public_pem: kp.public_pem,
                        message,
                        signature_b64: sig.signature,
                      },
                    });
                    return {
                      signature: sig.signature.slice(0, 40) + "…",
                      verified: ok.verified,
                    };
                  })
                }
              >
                Sign → verify (RFC 6979)
              </button>
              <button
                className="danger"
                disabled={status === "loading"}
                onClick={() =>
                  run(async () => {
                    const sig = await apiFetch("/ecdsa/sign", {
                      method: "POST",
                      body: {
                        private_pem: kp.private_pem,
                        message,
                        mode: "deterministic-rfc6979",
                      },
                    });
                    try {
                      await apiFetch("/ecdsa/verify", {
                        method: "POST",
                        body: {
                          public_pem: kp.public_pem,
                          message: message + "x",
                          signature_b64: sig.signature,
                        },
                      });
                      return { verified: true, note: "UNEXPECTEDLY VERIFIED" };
                    } catch (err) {
                      return {
                        verified: false,
                        tampered_message_rejected: err.message,
                      };
                    }
                  })
                }
              >
                Sign, tamper, verify
              </button>
            </div>
          )}

          {tab === "determinism" && (
            <div className="actions">
              <button
                disabled={status === "loading"}
                onClick={() =>
                  run(() =>
                    apiFetch("/ecdsa/determinism", {
                      method: "POST",
                      body: { private_pem: kp.private_pem, message },
                    }),
                  )
                }
              >
                Compare both modes
              </button>
            </div>
          )}

          {tab === "k-reuse" && (
            <>
              <p className="hint">
                The server signs BOTH messages with one fixed k — the classic
                fatal bug. Two public signatures, two equations, two unknowns
                (k, d). Watch the private key fall out.
              </p>
              <div className="actions">
                <button
                  className="danger"
                  disabled={status === "loading"}
                  onClick={() =>
                    run(() =>
                      apiFetch("/ecdsa/attack/k-reuse", {
                        method: "POST",
                        body: {
                          private_pem: kp.private_pem,
                          message1: message,
                          message2: message2,
                          demo: true,
                        },
                      }),
                    )
                  }
                >
                  Sign with reused k → recover key
                </button>
              </div>
            </>
          )}

          {status === "loading" && <Loading label="Signing…" />}
          <ErrorText error={error} />
          {result && (
            <KeyValue
              title="Result"
              entries={Object.entries(
                tab === "k-reuse"
                  ? {
                      shared_r: String(result.recovery.r_shared),
                      signature_1: `r=${result.signature1.r}, s=${result.signature1.s}`,
                      signature_2: `r=${result.signature2.r}, s=${result.signature2.s}`,
                      recovered_k: result.recovery.recovered_k,
                      recovered_private_key_d:
                        result.recovery.recovered_private_key_d,
                      RECOVERY_CONFIRMED: String(
                        result.recovery.recovery_confirmed,
                      ),
                      countermeasure: result.recovery.countermeasure,
                    }
                  : result,
              )}
            />
          )}
        </>
      )}
    </Panel>
  );
}
