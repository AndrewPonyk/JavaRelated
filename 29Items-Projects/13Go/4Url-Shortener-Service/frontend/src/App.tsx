import { UrlShortener } from "./components/UrlShortener";

export function App() {
  return (
    <main className="app-shell">
      <section className="workspace">
        <div className="page-heading">
          <p className="eyebrow">URL Shortener</p>
          <h1>Create and inspect short links</h1>
        </div>
        <UrlShortener />
      </section>
    </main>
  );
}

