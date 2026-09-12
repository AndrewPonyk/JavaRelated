import { AddressLookup } from "./components/AddressLookup";

export default function App() {
  return (
    <main className="app-shell">
      <section className="workspace">
        <div className="page-heading">
          <p className="eyebrow">Address operations</p>
          <h1>Geocoding lookup</h1>
        </div>
        <AddressLookup />
      </section>
    </main>
  );
}
