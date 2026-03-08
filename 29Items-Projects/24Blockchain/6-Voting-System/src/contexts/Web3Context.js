import React, { createContext, useContext, useState, useEffect, useCallback } from "react";
import { Web3 } from "web3";
import { SUPPORTED_CHAINS } from "../utils/constants";

const Web3Context = createContext(null);

export function Web3Provider({ children }) {
  const [web3, setWeb3] = useState(null);
  const [account, setAccount] = useState(null);
  const [chainId, setChainId] = useState(null);
  const [isConnecting, setIsConnecting] = useState(false);
  const [error, setError] = useState(null);

  // Auth state for API calls
  const [authData, setAuthData] = useState(null);

  const connectWallet = useCallback(async () => {
    if (!window.ethereum) {
      setError("Metamask not detected. Please install Metamask.");
      return;
    }

    setIsConnecting(true);
    setError(null);

    try {
      const accounts = await window.ethereum.request({ method: "eth_requestAccounts" });
      const web3Instance = new Web3(window.ethereum);
      // Removed transactionBlockTimeout override due to Web3 v4 strict config
      const chain = await window.ethereum.request({ method: "eth_chainId" });

      setWeb3(web3Instance);
      setAccount(accounts[0]);
      setChainId(Number(chain));
    } catch (err) {
      setError(err.message || "Failed to connect wallet");
    } finally {
      setIsConnecting(false);
    }
  }, []);

  const disconnectWallet = useCallback(() => {
    setWeb3(null);
    setAccount(null);
    setChainId(null);
    setAuthData(null);
  }, []);

  const clearAuth = useCallback(() => {
    setAuthData(null);
    setError(null);
  }, []);

  /**
   * Authenticate with the backend by signing a nonce.
   */
  const authenticate = useCallback(async () => {
    if (!web3 || !account) return null;

    try {
      // Request nonce from backend
      const API_URL = process.env.REACT_APP_API_URL || "http://localhost:3001/api";
      const nonceRes = await fetch(`${API_URL}/auth/nonce`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ walletAddress: account }),
      });

      if (!nonceRes.ok) throw new Error("Failed to get nonce");
      const { data } = await nonceRes.json();

      // |su:10) Web3 Authentication (Sign-in with Ethereum). Instead of passwords, Web3 apps use wallet signatures to prove identity. We ask the backend for a random "nonce", and the user mathematically signs it with their private key in MetaMask. The backend can verify this signature belongs to their exact address.
      // Sign the message with Metamask
      const signature = await window.ethereum.request({
        method: 'personal_sign',
        params: [data.message, account],
      });

      const auth = {
        walletAddress: account,
        nonce: data.nonce,
        signature,
      };
      setAuthData(auth);
      return auth;
    } catch (err) {
      setError(`Authentication failed: ${err.message}`);
      return null;
    }
  }, [web3, account]);

  // Listen for account and chain changes
  useEffect(() => {
    if (!window.ethereum) return;

    const handleAccountsChanged = (accounts) => {
      if (accounts.length === 0) {
        disconnectWallet();
      } else {
        setAccount(accounts[0]);
        setAuthData(null); // invalidate auth on account change
      }
    };

    const handleChainChanged = (newChainId) => {
      setChainId(Number(newChainId));
    };

    window.ethereum.on("accountsChanged", handleAccountsChanged);
    window.ethereum.on("chainChanged", handleChainChanged);

    return () => {
      window.ethereum.removeListener("accountsChanged", handleAccountsChanged);
      window.ethereum.removeListener("chainChanged", handleChainChanged);
    };
  }, [disconnectWallet]);

  const isConnected = !!account;
  const isSupported = chainId ? !!SUPPORTED_CHAINS[chainId] : false;
  const chainName = chainId ? SUPPORTED_CHAINS[chainId] || `Unknown (${chainId})` : null;

  const value = {
    web3,
    account,
    chainId,
    chainName,
    isConnecting,
    isConnected,
    isSupported,
    error,
    authData,
    connectWallet,
    disconnectWallet,
    authenticate,
    clearAuth,
    setError,
  };

  return <Web3Context.Provider value={value}>{children}</Web3Context.Provider>;
}

export function useWeb3Context() {
  const context = useContext(Web3Context);
  if (!context) {
    throw new Error("useWeb3Context must be used within a Web3Provider");
  }
  return context;
}

export default Web3Context;
