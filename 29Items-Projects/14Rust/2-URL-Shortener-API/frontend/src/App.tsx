import { useState } from "react";
import { ShortenUrlForm } from "./components/ShortenUrlForm";

export function App() {
  const [copied, setCopied] = useState(false);

  function showCopyConfirmation() {
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1_800);
  }

  return (
    <main>
      <header>
        <p className="eyebrow">URL SHORTENER</p>
        <h1>Make every link memorable.</h1>
        <p>Create short, reliable links with visit tracking.</p>
      </header>
      <ShortenUrlForm onCopy={showCopyConfirmation} />
      <p className="hint" aria-live="polite">
        {copied
          ? "Link copied to clipboard."
          : "Links are stored securely and redirect with HTTP 302."}
      </p>
    </main>
  );
}
