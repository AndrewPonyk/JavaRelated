import { Link, NavLink, Route, Routes } from "react-router-dom";
import { auth } from "./api/client.js";
import CryptoLab from "./components/CryptoLab.jsx";
import RsaLab from "./components/RsaLab.jsx";
import EcdsaLab from "./components/EcdsaLab.jsx";
import HashPanel from "./components/HashPanel.jsx";
import PasswordPanel from "./components/PasswordPanel.jsx";
import TlsWalkthrough from "./components/TlsWalkthrough.jsx";
import AttackGallery from "./components/AttackGallery.jsx";
import Lessons from "./components/Lessons.jsx";
import LessonDetail from "./components/LessonDetail.jsx";
import Login from "./components/Login.jsx";
import AuditLog from "./components/AuditLog.jsx";
import StatusBadge from "./components/StatusBadge.jsx";

export default function App() {
  const signedIn = Boolean(auth.getToken());
  return (
    <>
      <header className="site-header">
        <Link to="/" className="brand">
          Cryptography Toolkit
        </Link>
        <nav className="main-nav" aria-label="Main">
          <NavLink to="/" end>
            AES
          </NavLink>
          <NavLink to="/rsa">RSA</NavLink>
          <NavLink to="/ecdsa">ECDSA</NavLink>
          <NavLink to="/hashes">Hashes</NavLink>
          <NavLink to="/tls">TLS 1.3</NavLink>
          <NavLink to="/attacks">Attack Gallery</NavLink>
          <NavLink to="/lessons">Lessons</NavLink>
          <NavLink to="/api-docs" reloadDocument>
            API
          </NavLink>
          {signedIn ? (
            <>
              <NavLink to="/admin/audit">Audit</NavLink>
              <button
                className="linklike"
                onClick={() => {
                  auth.setToken(null);
                  location.assign("/");
                }}
              >
                Sign out
              </button>
            </>
          ) : (
            <NavLink to="/login">Sign in</NavLink>
          )}
        </nav>
      </header>

      <main className="app">
        <Routes>
          <Route path="/" element={<CryptoLab />} />
          <Route path="/rsa" element={<RsaLab />} />
          <Route path="/ecdsa" element={<EcdsaLab />} />
          <Route
            path="/hashes"
            element={
              <>
                <HashPanel />
                <PasswordPanel />
              </>
            }
          />
          <Route path="/tls" element={<TlsWalkthrough />} />
          <Route path="/attacks" element={<AttackGallery />} />
          <Route path="/lessons" element={<Lessons />} />
          <Route path="/lessons/:slug" element={<LessonDetail />} />
          <Route path="/login" element={<Login />} />
          <Route path="/admin/audit" element={<AuditLog />} />
          <Route
            path="*"
            element={
              <section className="card">
                <h2>
                  Not found <StatusBadge status="error" />
                </h2>
                <p>
                  That page doesn&apos;t exist. Head back to the{" "}
                  <Link to="/">AES lab</Link>.
                </p>
              </section>
            }
          />
        </Routes>
      </main>

      <footer className="site-footer">
        Educational platform — every demo pairs an attack with its
        countermeasure. Demo secrets are ephemeral and never persisted.
      </footer>
    </>
  );
}
