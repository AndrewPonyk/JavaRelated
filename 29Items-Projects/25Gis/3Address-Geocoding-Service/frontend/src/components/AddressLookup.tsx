import { FormEvent, useState } from "react";

import { AddressLookupResponse, lookupAddress } from "../lib/api";

export function AddressLookup() {
  const [query, setQuery] = useState("");
  const [result, setResult] = useState<AddressLookupResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setResult(null);
    setIsLoading(true);

    try {
      const response = await lookupAddress(query);
      setResult(response);
    } catch (lookupError) {
      setError(lookupError instanceof Error ? lookupError.message : "Lookup failed");
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <div className="lookup-panel">
      <form className="lookup-form" onSubmit={handleSubmit}>
        <label htmlFor="address-query">Address</label>
        <div className="lookup-row">
          <input
            id="address-query"
            name="address"
            placeholder="1600 Pennsylvania Ave NW"
            value={query}
            minLength={3}
            onChange={(event) => setQuery(event.target.value)}
          />
          <button type="submit" disabled={isLoading || query.trim().length < 3}>
            {isLoading ? "Searching" : "Lookup"}
          </button>
        </div>
      </form>

      {error ? <p className="error-state">{error}</p> : null}

      {result ? (
        <div className="result-block" aria-live="polite">
          <h2>{result.best_match.formatted_address}</h2>
          <dl>
            <div>
              <dt>Latitude</dt>
              <dd>{result.best_match.latitude}</dd>
            </div>
            <div>
              <dt>Longitude</dt>
              <dd>{result.best_match.longitude}</dd>
            </div>
            <div>
              <dt>Confidence</dt>
              <dd>{Math.round(result.best_match.confidence * 100)}%</dd>
            </div>
            <div>
              <dt>Source</dt>
              <dd>{result.best_match.source}</dd>
            </div>
          </dl>
        </div>
      ) : null}
    </div>
  );
}
