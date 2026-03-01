import React from "react";
import { Link } from "react-router-dom";
import { useWeb3 } from "../../hooks/useWeb3";

function Header() {
  const { account, isConnecting, connectWallet, disconnectWallet, isConnected, chainName, isSupported } = useWeb3();

  const shortenAddress = (addr) => `${addr.slice(0, 6)}...${addr.slice(-4)}`;

  return (
    <header className="header">
      <div className="header-content">
        <Link to="/" className="logo">
          Voting System
        </Link>

        <nav className="nav">
          <Link to="/">Proposals</Link>
          <Link to="/delegation">Delegation</Link>
        </nav>

        <div className="wallet-section">
          {isConnected ? (
            <div className="wallet-info">
              <span className={`chain-badge ${isSupported ? "" : "chain-unsupported"}`}>
                {chainName || "Unknown"}
              </span>
              <span className="address">{shortenAddress(account)}</span>
              <button onClick={disconnectWallet} className="btn-disconnect">
                Disconnect
              </button>
            </div>
          ) : (
            <button onClick={connectWallet} disabled={isConnecting} className="btn-connect">
              {isConnecting ? "Connecting..." : "Connect Wallet"}
            </button>
          )}
        </div>
      </div>
    </header>
  );
}

export default Header;
