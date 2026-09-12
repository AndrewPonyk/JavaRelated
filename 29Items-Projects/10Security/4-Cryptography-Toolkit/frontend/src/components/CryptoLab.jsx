import { useState } from "react";
import { apiFetch } from "../api/client.js";
import { hasErrors, randomKeyB64, validateAesForm } from "../lib/validation.js";
import { Panel, ErrorText, Field, KeyValue, Loading } from "./ui.jsx";

/**
 * AES lab: generate a key, encrypt (GCM default; CBC/ECB as labelled demos),
 * then decrypt the result back — tamper with the ciphertext to watch the
 * GCM tag check fail.
 */
export default function CryptoLab() {
  const [plaintext, setPlaintext] = useState("attack at dawn");
  const [keyB64, setKeyB64] = useState(() => randomKeyB64(32));
  const [mode, setMode] = useState("gcm");
  const [demo, setDemo] = useState(false);
  const [formErrors, setFormErrors] = useState({});
  const [status, setStatus] = useState("idle");
  const [error, setError] = useState(null);
  const [enc, setEnc] = useState(null);
  const [dec, setDec] = useState(null);

  async function run(fn) {
    setStatus("loading");
    setError(null);
    setDec(null);
    try {
      await fn();
      setStatus("ok");
    } catch (err) {
      setError(err.message);
      setStatus("error");
    }
  }

  const doEncrypt = () => {
    const errors = validateAesForm({ plaintext, keyB64, mode, demo });
    setFormErrors(errors);
    if (hasErrors(errors)) return;
    return run(async () => {
      const data = await apiFetch("/aes/encrypt", {
        method: "POST",
        body: { plaintext, key_b64: keyB64, mode, demo: mode !== "gcm" },
      });
      setEnc(data);
    });
  };

  const doDecrypt = () =>
    run(async () => {
      const data = await apiFetch("/aes/decrypt", {
        method: "POST",
        body: {
          ciphertext_b64: enc.ciphertext,
          iv_b64: enc.iv ?? "",
          tag_b64: enc.tag ?? "",
          key_b64: keyB64,
          mode,
        },
      });
      setDec(data);
    });

  /** Flip one bit of the ciphertext — GCM must refuse the tag. */
  const doTamper = () => {
    const bytes = atob(enc.ciphertext);
    const corrupted =
      String.fromCharCode(bytes.charCodeAt(0) ^ 0x01) + bytes.slice(1);
    setEnc({ ...enc, ciphertext: btoa(corrupted) });
    setDec(null);
  };

  return (
    <Panel
      title="AES lab — authenticated encryption"
      status={status}
      subtitle="AES-256-GCM by default. CBC/ECB exist only to demonstrate their weaknesses (demo checkbox)."
    >
      <Field label="Plaintext" error={formErrors.plaintext}>
        <textarea
          value={plaintext}
          onChange={(e) => setPlaintext(e.target.value)}
          rows={3}
          data-testid="plaintext"
        />
      </Field>

      <Field
        label="Key (base64, 32 bytes — fresh from the CSPRNG)"
        error={formErrors.key}
      >
        <input
          value={keyB64}
          onChange={(e) => setKeyB64(e.target.value)}
          data-testid="key"
        />
      </Field>
      <div className="inline-row">
        <button
          className="secondary"
          onClick={() => setKeyB64(randomKeyB64(32))}
        >
          New key
        </button>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={demo}
            onChange={(e) => setDemo(e.target.checked)}
          />{" "}
          attack-gallery modes
        </label>
        <select
          value={mode}
          onChange={(e) => setMode(e.target.value)}
          aria-label="AES mode"
        >
          <option value="gcm">GCM (secure)</option>
          <option value="cbc" disabled={!demo}>
            CBC (gallery)
          </option>
          <option value="ecb" disabled={!demo}>
            ECB (gallery)
          </option>
        </select>
        {formErrors.mode && (
          <span className="field-error" role="alert">
            {formErrors.mode}
          </span>
        )}
      </div>

      <div className="actions">
        <button onClick={doEncrypt} disabled={status === "loading"}>
          Encrypt
        </button>
        {enc && (
          <button onClick={doDecrypt} disabled={status === "loading"}>
            Decrypt
          </button>
        )}
        {enc && mode === "gcm" && (
          <button
            className="danger"
            onClick={doTamper}
            disabled={status === "loading"}
          >
            Flip 1 bit &amp; decrypt
          </button>
        )}
      </div>

      {status === "loading" && <Loading label="Running AES…" />}
      <ErrorText error={error} />

      {enc && (
        <KeyValue
          title="Ciphertext"
          entries={[
            ["mode", enc.mode],
            ["iv", enc.iv ?? "—"],
            ["ciphertext", enc.ciphertext],
            ["gcm tag", enc.tag ?? "—"],
            ...(enc.warning ? [["⚠️", enc.warning]] : []),
          ]}
        />
      )}
      {dec && (
        <KeyValue title="Decrypted" entries={[["plaintext", dec.plaintext]]} />
      )}
      {dec === null && error && status === "error" && (
        <p className="hint">
          Tampered ciphertext → the tag check fails and nothing decrypts.
          That&apos;s AEAD working.
        </p>
      )}
    </Panel>
  );
}
