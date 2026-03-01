import React, { useState, useEffect, useMemo } from "react";
import { useWeb3 } from "../../hooks/useWeb3";
import * as api from "../../utils/api";
import { CONTRACT_ADDRESSES } from "../../utils/constants";

function DelegateVote() {
  const { web3, account, isConnected } = useWeb3();

  const [delegateAddress, setDelegateAddress] = useState("");
  const [delegationInfo, setDelegationInfo] = useState(null);
  const [votingPower, setVotingPower] = useState(1);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [txHash, setTxHash] = useState(null);

  // Load DelegationRegistry ABI (Hardhat artifact path, with Truffle fallback)
  const [delegationAbi, setDelegationAbi] = useState(null);
  useEffect(() => {
    fetch("/artifacts/contracts/DelegationRegistry.sol/DelegationRegistry.json")
      .then((r) => r.ok ? r.json() : fetch("/contracts/DelegationRegistry.json"))
      .then((r) => r.json ? r.json() : r)
      .then(setDelegationAbi)
      .catch(() => setDelegationAbi(null));
  }, []);

  const delegationContract = useMemo(() => {
    if (!web3 || !delegationAbi || !CONTRACT_ADDRESSES.delegationRegistry) return null;
    try {
      return new web3.eth.Contract(delegationAbi.abi, CONTRACT_ADDRESSES.delegationRegistry);
    } catch {
      return null;
    }
  }, [web3, delegationAbi]);

  useEffect(() => {
    if (isConnected && account) {
      fetchDelegationData();
    }
  }, [account, isConnected]);

  async function fetchDelegationData() {
    try {
      const [infoRes, powerRes, historyRes] = await Promise.all([
        api.getDelegationInfo(account).catch(() => null),
        api.getVotingPower(account).catch(() => null),
        api.getDelegationHistory(account).catch(() => null),
      ]);

      if (infoRes?.data) setDelegationInfo(infoRes.data);
      if (powerRes?.data) setVotingPower(powerRes.data.votingPower);
      if (historyRes?.data) setHistory(historyRes.data);
    } catch (err) {
      console.error("Failed to fetch delegation data:", err);
    }
  }

  async function handleDelegate(e) {
    e.preventDefault();
    if (!delegateAddress || !isConnected) return;

    setLoading(true);
    setError(null);
    setTxHash(null);

    try {
      if (delegationContract) {
        const tx = await delegationContract.methods
          .delegateVote(delegateAddress, 0) // 0 = global delegation
          .send({ from: account });
        setTxHash(tx.transactionHash);
      }
      setDelegateAddress("");
      await fetchDelegationData();
    } catch (err) {
      const msg = err.message || "Failed to delegate";
      if (msg.includes("revert")) {
        const match = msg.match(/reason string '(.+?)'/);
        setError(match ? match[1] : "Transaction reverted");
      } else if (msg.includes("User denied")) {
        setError("Transaction rejected by user");
      } else {
        setError(msg);
      }
    } finally {
      setLoading(false);
    }
  }

  async function handleRevoke() {
    setLoading(true);
    setError(null);
    setTxHash(null);

    try {
      if (delegationContract) {
        const tx = await delegationContract.methods
          .revokeDelegation(0) // 0 = global
          .send({ from: account });
        setTxHash(tx.transactionHash);
      }
      await fetchDelegationData();
    } catch (err) {
      const msg = err.message || "Failed to revoke";
      if (msg.includes("revert")) {
        const match = msg.match(/reason string '(.+?)'/);
        setError(match ? match[1] : "Transaction reverted");
      } else {
        setError(msg);
      }
    } finally {
      setLoading(false);
    }
  }

  if (!isConnected) {
    return <div className="delegate-vote"><p className="info-message">Connect your wallet to manage delegation.</p></div>;
  }

  const shortenAddr = (addr) => addr ? `${addr.slice(0, 6)}...${addr.slice(-4)}` : "";

  return (
    <div className="delegate-vote">
      <h3>Vote Delegation</h3>

      <div className="delegation-stats">
        <div className="stat-card">
          <span className="stat-label">Your Voting Power</span>
          <span className="stat-value">{votingPower}</span>
        </div>
        {delegationInfo?.delegatedTo && (
          <div className="stat-card">
            <span className="stat-label">Delegated To</span>
            <span className="stat-value">{shortenAddr(delegationInfo.delegatedTo.to_address)}</span>
          </div>
        )}
        {delegationInfo?.receivedDelegations?.length > 0 && (
          <div className="stat-card">
            <span className="stat-label">Received Delegations</span>
            <span className="stat-value">{delegationInfo.receivedDelegations.length}</span>
          </div>
        )}
      </div>

      {error && <div className="error-message">{error}</div>}
      {txHash && <p className="tx-hash success-message">Tx: {txHash.slice(0, 10)}...{txHash.slice(-8)}</p>}

      {delegationInfo?.delegatedTo ? (
        <div className="revoke-section">
          <p>You have an active delegation to <strong>{shortenAddr(delegationInfo.delegatedTo.to_address)}</strong></p>
          <button onClick={handleRevoke} disabled={loading} className="btn btn-danger">
            {loading ? "Revoking..." : "Revoke Delegation"}
          </button>
        </div>
      ) : (
        <form onSubmit={handleDelegate} className="delegate-form">
          <div className="form-group">
            <label htmlFor="delegate-address">Delegate To (Ethereum Address)</label>
            <input
              id="delegate-address"
              type="text"
              value={delegateAddress}
              onChange={(e) => setDelegateAddress(e.target.value)}
              placeholder="0x..."
              pattern="^0x[a-fA-F0-9]{40}$"
              required
            />
          </div>
          <button type="submit" disabled={loading} className="btn btn-primary">
            {loading ? "Delegating..." : "Delegate Vote"}
          </button>
        </form>
      )}

      {delegationInfo?.receivedDelegations?.length > 0 && (
        <div className="received-delegations">
          <h4>Delegators</h4>
          <ul>
            {delegationInfo.receivedDelegations.map((d, i) => (
              <li key={i}>{shortenAddr(d.from_address)}</li>
            ))}
          </ul>
        </div>
      )}

      {history.length > 0 && (
        <div className="delegation-history">
          <h4>History</h4>
          <table>
            <thead>
              <tr><th>Type</th><th>From</th><th>To</th><th>Date</th></tr>
            </thead>
            <tbody>
              {history.slice(0, 10).map((h, i) => (
                <tr key={i}>
                  <td><span className={`badge badge-${h.event_type}`}>{h.event_type}</span></td>
                  <td>{shortenAddr(h.from_address)}</td>
                  <td>{shortenAddr(h.to_address)}</td>
                  <td>{new Date(h.created_at).toLocaleDateString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default DelegateVote;
