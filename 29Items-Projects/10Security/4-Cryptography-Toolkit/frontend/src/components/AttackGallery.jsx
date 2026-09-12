import { apiFetch } from "../api/client.js";
import { useAsync } from "../hooks/useAsync.js";
import { ErrorText, Field, KeyValue, Loading, Mono, Panel } from "./ui.jsx";
import { useState } from "react";

function PenguinGrid({ title, rows, className }) {
  return (
    <div className={`penguin ${className ?? ""}`}>
      <h4>{title}</h4>
      <pre aria-label={title}>{rows.join("\n")}</pre>
    </div>
  );
}

/** Attack gallery: every classic break, each paired with its countermeasure. */
export default function AttackGallery() {
  const penguin = useAsync(null);
  const extension = useAsync(null);
  const gcm = useAsync(null);
  const [extForm, setExtForm] = useState({
    message: "amount=100&to=alice",
    append: "&admin=true",
  });
  const [gcmForm, setGcmForm] = useState({
    known: "transfer: 100 USD to Bob",
    secret: "transfer: 900 USD to Eve",
  });

  return (
    <>
      <Panel
        title="Attack gallery"
        subtitle="Every demo is real cryptography used wrongly — each result ends with the countermeasure."
      >
        <p className="hint">
          ECB penguin · SHA-256 length extension · GCM nonce reuse ·
          textbook-RSA malleability (in the RSA lab) · ECDSA k-reuse (in the
          ECDSA lab).
        </p>
      </Panel>

      <Panel
        title="① ECB penguin — patterns survive encryption"
        status={penguin.status}
      >
        <div className="actions">
          <button
            onClick={() =>
              penguin.run(() =>
                apiFetch("/attacks/ecb-penguin", { method: "POST", body: {} }),
              )
            }
          >
            Run ECB penguin
          </button>
        </div>
        {penguin.status === "loading" && <Loading />}
        <ErrorText error={penguin.error} />
        {penguin.data && (
          <>
            <div className="penguin-grid">
              <PenguinGrid title="Plaintext" rows={penguin.data.plaintext} />
              <PenguinGrid
                title="ECB ciphertext"
                rows={penguin.data.ecb_blocks}
                className="bad"
              />
              <PenguinGrid
                title="CBC ciphertext"
                rows={penguin.data.cbc_blocks}
                className="good"
              />
            </div>
            <p className="hint">{penguin.data.observation}</p>
            <p className="hint">
              <strong>Countermeasure:</strong> {penguin.data.countermeasure}
            </p>
          </>
        )}
      </Panel>

      <Panel
        title="② Length extension — SHA-256(key‖msg) MAC forgery"
        status={extension.status}
      >
        <Field label="Protected message (the 'server' MACs it with a secret key)">
          <input
            value={extForm.message}
            onChange={(e) =>
              setExtForm((f) => ({ ...f, message: e.target.value }))
            }
          />
        </Field>
        <Field label="Attacker's appended text">
          <input
            value={extForm.append}
            onChange={(e) =>
              setExtForm((f) => ({ ...f, append: e.target.value }))
            }
          />
        </Field>
        <div className="actions">
          <button
            className="danger"
            onClick={() =>
              extension.run(() =>
                apiFetch("/attacks/length-extension", {
                  method: "POST",
                  body: extForm,
                }),
              )
            }
          >
            Forge MAC without the key
          </button>
        </div>
        {extension.status === "loading" && <Loading />}
        <ErrorText error={extension.error} />
        {extension.data && (
          <>
            <KeyValue
              entries={[
                ["original MAC", extension.data.original_mac_hex],
                ["glue padding", extension.data.glue_padding_hex],
                ["forged MAC", extension.data.forged_mac_hex],
                [
                  "naive SHA256(key‖msg) server accepts forgery",
                  String(extension.data.server_accepts_naive_sha256_key_msg),
                ],
                [
                  "HMAC server accepts forgery",
                  String(extension.data.server_accepts_hmac),
                ],
              ]}
            />
            <p className="hint">{extension.data.observation}</p>
            <p className="hint">
              <strong>Countermeasure:</strong> {extension.data.countermeasure}
            </p>
          </>
        )}
      </Panel>

      <Panel
        title="③ GCM nonce reuse — keystream XOR recovery"
        status={gcm.status}
      >
        <Field label="Known plaintext (attacker legitimately has this)">
          <input
            value={gcmForm.known}
            onChange={(e) =>
              setGcmForm((f) => ({ ...f, known: e.target.value }))
            }
          />
        </Field>
        <Field label="Secret plaintext (same length as known!)">
          <input
            value={gcmForm.secret}
            onChange={(e) =>
              setGcmForm((f) => ({ ...f, secret: e.target.value }))
            }
          />
        </Field>
        <div className="actions">
          <button
            className="danger"
            onClick={() =>
              gcm.run(() =>
                apiFetch("/attacks/gcm-nonce-reuse", {
                  method: "POST",
                  body: {
                    known_plaintext: gcmForm.known,
                    secret_plaintext: gcmForm.secret,
                  },
                }),
              )
            }
          >
            Reuse the nonce, read the secret
          </button>
        </div>
        {gcm.status === "loading" && <Loading />}
        <ErrorText error={gcm.error} />
        {gcm.data && (
          <>
            <Mono lines={[`C1 ⊕ C2 = ${gcm.data.xor_hex}`]} />
            <KeyValue
              entries={[
                ["recovered secret", gcm.data.recovered_secret],
                ["attacker needed", "the nonce reuse + one known plaintext"],
              ]}
            />
            <p className="hint">{gcm.data.observation}</p>
            <p className="hint">
              <strong>Countermeasure:</strong> {gcm.data.countermeasure}
            </p>
          </>
        )}
      </Panel>
    </>
  );
}
