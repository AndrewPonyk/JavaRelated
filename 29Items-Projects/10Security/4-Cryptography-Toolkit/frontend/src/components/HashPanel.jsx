import { useState } from "react";
import { apiFetch } from "../api/client.js";
import { ErrorText, Field, KeyValue, Loading, Panel } from "./ui.jsx";

const ALGOS = ["sha3-256", "sha3-384", "sha3-512"];

/** SHA-3 hashing panel — try "abc" for the FIPS 202 test vector. */
export default function HashPanel() {
  const [message, setMessage] = useState("abc");
  const [algorithm, setAlgorithm] = useState("sha3-256");
  const [result, setResult] = useState(null);
  const [status, setStatus] = useState("idle");
  const [error, setError] = useState(null);

  const digest = async () => {
    if (!message.trim()) {
      setError("Message is required.");
      setStatus("error");
      return;
    }
    setStatus("loading");
    setError(null);
    try {
      setResult(
        await apiFetch("/sha3/digest", {
          method: "POST",
          body: { message, algorithm },
        }),
      );
      setStatus("ok");
    } catch (err) {
      setError(err.message);
      setStatus("error");
    }
  };

  return (
    <Panel
      title="SHA-3 hashing"
      status={status}
      subtitle='Hash with SHA3-256/384/512. "abc" → 3a985da7… (FIPS 202).'
    >
      <Field label="Message">
        <textarea
          rows={2}
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          data-testid="sha3-message"
        />
      </Field>
      <div className="inline-row">
        <select
          value={algorithm}
          onChange={(e) => setAlgorithm(e.target.value)}
          aria-label="Algorithm"
        >
          {ALGOS.map((a) => (
            <option key={a}>{a}</option>
          ))}
        </select>
        <button
          onClick={digest}
          disabled={status === "loading"}
          data-testid="sha3-run"
        >
          Hash
        </button>
      </div>
      {status === "loading" && <Loading />}
      <ErrorText error={error} />
      {result && (
        <KeyValue
          entries={[
            ["algorithm", result.algorithm],
            ["digest bits", result.digest_bits],
            ["digest (hex)", result.digest_hex],
          ]}
        />
      )}
    </Panel>
  );
}
