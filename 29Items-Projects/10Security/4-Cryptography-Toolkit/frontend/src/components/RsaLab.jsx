import { useState } from "react";
import { apiFetch } from "../api/client.js";
import { ErrorText, Field, KeyValue, Loading, Panel } from "./ui.jsx";

const TABS = [
  ["encrypt", "OAEP encrypt/decrypt"],
  ["sign", "PSS sign/verify"],
  ["malleability", "Textbook RSA (gallery)"],
];

/** RSA lab: keygen once, then exercise OAEP / PSS / the malleability demo. */
export default function RsaLab() {
  const [tab, setTab] = useState("encrypt");
  const [kp, setKp] = useState(null);
  const [message, setMessage] = useState("short message");
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

  const keygen = () =>
    run(async () =>
      setKp(
        await apiFetch("/rsa/keygen", { method: "POST", body: { bits: 2048 } }),
      ),
    );

  return (
    <Panel
      title="RSA lab — the padding IS the protocol"
      status={kp ? status : "idle"}
      subtitle="OAEP for encryption, PSS for signatures. Textbook RSA appears only in the gallery tab."
    >
      {!kp ? (
        <>
          <p>
            Generate a demo keypair to start (2048-bit minimum — 1024 is
            broken).
          </p>
          <div className="actions">
            <button onClick={keygen} disabled={status === "loading"}>
              Generate keypair
            </button>
          </div>
        </>
      ) : (
        <>
          <KeyValue
            title="Keypair (demo — in production the private key never leaves the client/HSM)"
            entries={[
              ["bits", kp.bits],
              ["e", kp.e],
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

          <Field label={tab === "sign" ? "Message to sign" : "Message"}>
            <textarea
              rows={2}
              value={message}
              onChange={(e) => setMessage(e.target.value)}
            />
          </Field>

          {tab === "encrypt" && (
            <div className="actions">
              <button
                disabled={status === "loading"}
                onClick={() =>
                  run(async () => {
                    const enc = await apiFetch("/rsa/encrypt", {
                      method: "POST",
                      body: { public_pem: kp.public_pem, plaintext: message },
                    });
                    const dec = await apiFetch("/rsa/decrypt", {
                      method: "POST",
                      body: {
                        private_pem: kp.private_pem,
                        ciphertext_b64: enc.ciphertext,
                      },
                    });
                    return {
                      ciphertext: enc.ciphertext,
                      decrypted: dec.plaintext,
                    };
                  })
                }
              >
                Encrypt → decrypt (round trip)
              </button>
            </div>
          )}

          {tab === "sign" && (
            <div className="actions">
              <button
                disabled={status === "loading"}
                onClick={() =>
                  run(async () => {
                    const sig = await apiFetch("/rsa/sign", {
                      method: "POST",
                      body: { private_pem: kp.private_pem, message },
                    });
                    const verifyOk = await apiFetch("/rsa/verify", {
                      method: "POST",
                      body: {
                        public_pem: kp.public_pem,
                        message,
                        signature_b64: sig.signature,
                      },
                    });
                    return {
                      signature: sig.signature,
                      verified: verifyOk.verified,
                    };
                  })
                }
              >
                Sign → verify (round trip)
              </button>
              <button
                className="danger"
                disabled={status === "loading"}
                onClick={() =>
                  run(async () => {
                    const sig = await apiFetch("/rsa/sign", {
                      method: "POST",
                      body: { private_pem: kp.private_pem, message },
                    });
                    try {
                      await apiFetch("/rsa/verify", {
                        method: "POST",
                        body: {
                          public_pem: kp.public_pem,
                          message: message + "!",
                          signature_b64: sig.signature,
                        },
                      });
                      return {
                        verified: true,
                        tampered: "UNEXPECTEDLY VERIFIED",
                      };
                    } catch (err) {
                      return { verified: false, tampered: err.message };
                    }
                  })
                }
              >
                Sign, tamper message, verify
              </button>
            </div>
          )}

          {tab === "malleability" && (
            <>
              <p className="hint">
                Raw RSA c=m^e mod n is deterministic and multiplicatively
                malleable — the attacker needs only the public key. Keep the
                message short (its integer must be &lt; n/2).
              </p>
              <div className="actions">
                <button
                  className="danger"
                  disabled={status === "loading" || message.length > 64}
                  onClick={() =>
                    run(() =>
                      apiFetch("/rsa/attack/malleability", {
                        method: "POST",
                        body: {
                          public_pem: kp.public_pem,
                          private_pem: kp.private_pem,
                          message,
                          demo: true,
                        },
                      }),
                    )
                  }
                >
                  Run malleability attack
                </button>
              </div>
            </>
          )}

          {status === "loading" && (
            <Loading label="Big-integer math in flight…" />
          )}
          <ErrorText error={error} />
          {result && (
            <KeyValue
              title="Result"
              entries={Object.entries(
                tab === "malleability"
                  ? {
                      deterministic: String(result.deterministic),
                      original_ct:
                        result.original_ciphertext_hex?.slice(0, 32) + "…",
                      attacker_ct:
                        result.attacker_ciphertext_hex?.slice(0, 32) + "…",
                      decrypted_int: result.decrypted_int,
                      countermeasure: result.countermeasure,
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
