import { SearchBar } from "@/components/SearchBar";

// Server component shell; the interactive search lives in the SearchBar client component.
export default function SearchPage() {
  return (
    <section>
      <h1>Search</h1>
      <p style={{ opacity: 0.75, marginTop: "-0.5rem" }}>
        Pick a backend and mode, then query. Results are ranked by similarity score.
      </p>
      <SearchBar />
    </section>
  );
}
