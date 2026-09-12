import React from "react";
import { createRoot } from "react-dom/client";
import { TranspilePanel } from "./components/TranspilePanel";
import "./styles.css";

const root = document.getElementById("root");

if (!root) {
  throw new Error("root element not found");
}

createRoot(root).render(
  <React.StrictMode>
    <TranspilePanel />
  </React.StrictMode>
);
