import { API_BASE_URL } from "./constants";

/**
 * Fetch wrapper with error handling and auth header injection.
 */
async function apiFetch(path, options = {}) {
  const url = `${API_BASE_URL}${path}`;
  const headers = { "Content-Type": "application/json", ...options.headers };

  const response = await fetch(url, { ...options, headers });

  if (!response.ok) {
    let errorData;
    try {
      errorData = await response.json();
    } catch {
      errorData = { error: { message: response.statusText } };
    }
    const err = new Error(errorData.error?.message || `Request failed: ${response.status}`);
    err.code = errorData.error?.code;
    err.status = response.status;
    throw err;
  }

  if (response.status === 204) return null;
  return response.json();
}

/**
 * Build auth headers from stored auth data.
 */
function authHeaders(authData) {
  if (!authData) return {};
  return {
    "x-wallet-address": authData.walletAddress,
    "x-signature": authData.signature,
    "x-nonce": authData.nonce,
  };
}

// --- Auth ---
export async function requestNonce(walletAddress) {
  return apiFetch("/auth/nonce", {
    method: "POST",
    body: JSON.stringify({ walletAddress }),
  });
}

export async function getProfile(auth) {
  return apiFetch("/auth/me", { headers: authHeaders(auth) });
}

// --- Proposals ---
export async function listProposals({ phase, page, limit } = {}) {
  const params = new URLSearchParams();
  if (phase) params.set("phase", phase);
  if (page) params.set("page", page);
  if (limit) params.set("limit", limit);
  return apiFetch(`/proposals?${params}`);
}

export async function getProposal(id) {
  return apiFetch(`/proposals/${id}`);
}

export async function createProposal(data, auth) {
  return apiFetch("/proposals", {
    method: "POST",
    headers: authHeaders(auth),
    body: JSON.stringify(data),
  });
}

export async function updateProposal(id, data, auth) {
  return apiFetch(`/proposals/${id}`, {
    method: "PUT",
    headers: authHeaders(auth),
    body: JSON.stringify(data),
  });
}

export async function deleteProposal(id, auth) {
  return apiFetch(`/proposals/${id}`, {
    method: "DELETE",
    headers: authHeaders(auth),
  });
}

export async function addAmendment(proposalId, content, auth) {
  return apiFetch(`/proposals/${proposalId}/amendments`, {
    method: "POST",
    headers: authHeaders(auth),
    body: JSON.stringify({ content }),
  });
}

// --- Votes ---
export async function getResults(proposalId) {
  return apiFetch(`/proposals/${proposalId}/results`);
}

export async function getCommitCount(proposalId) {
  return apiFetch(`/proposals/${proposalId}/commits`);
}

export async function getVotingStatus(proposalId) {
  return apiFetch(`/proposals/${proposalId}/status`);
}

// --- Delegation ---
export async function getDelegationInfo(address) {
  return apiFetch(`/delegation/${address}`);
}

export async function getVotingPower(address) {
  return apiFetch(`/delegation/${address}/power`);
}

export async function getDelegationHistory(address) {
  return apiFetch(`/delegation/${address}/history`);
}
