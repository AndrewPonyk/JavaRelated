import React from "react";
import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import Header from "./components/Layout/Header";
import Footer from "./components/Layout/Footer";
import ErrorBoundary from "./components/common/ErrorBoundary";
import Home from "./pages/Home";
import ProposalPage from "./pages/ProposalPage";
import DelegationPage from "./pages/DelegationPage";
import "./App.css";

function App() {
  return (
    <Router>
      <ErrorBoundary>
        <div className="app">
          <Header />
          <main className="main-content">
            <Routes>
              <Route path="/" element={<Home />} />
              <Route path="/proposals/:id" element={<ProposalPage />} />
              <Route path="/delegation" element={<DelegationPage />} />
            </Routes>
          </main>
          <Footer />
        </div>
      </ErrorBoundary>
    </Router>
  );
}

export default App;
