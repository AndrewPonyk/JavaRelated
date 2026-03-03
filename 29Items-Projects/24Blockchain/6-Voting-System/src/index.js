import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import { Web3Provider } from "./contexts/Web3Context";

// Web3.js v4 returns BigInt values which JSON.stringify cannot serialize.
// eslint-disable-next-line no-extend-native
BigInt.prototype.toJSON = function () {
  return this.toString();
};

const root = ReactDOM.createRoot(document.getElementById("root"));
root.render(
  <React.StrictMode>
    <Web3Provider>
      <App />
    </Web3Provider>
  </React.StrictMode>
);
