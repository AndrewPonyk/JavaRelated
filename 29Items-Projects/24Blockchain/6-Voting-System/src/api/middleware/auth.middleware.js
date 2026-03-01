const blockchainService = require("../services/blockchain.service");
const db = require("../utils/db");
const crypto = require("crypto");
const logger = require("../utils/logger");

/**
 * Generate a new authentication nonce for a wallet address.
 * The nonce must be signed by the wallet to authenticate.
 */
async function generateNonce(walletAddress) {
  const normalized = walletAddress.toLowerCase();
  const nonce = crypto.randomBytes(32).toString("hex");
  const expiresAt = new Date(Date.now() + 10 * 60 * 1000); // 10 minutes

  // Upsert user if they don't exist
  await db.query(
    `INSERT INTO users (wallet_address) VALUES ($1) ON CONFLICT (wallet_address) DO NOTHING`,
    [normalized]
  );

  // Insert the nonce
  await db.query(
    `INSERT INTO auth_nonces (wallet_address, nonce, expires_at) VALUES ($1, $2, $3)`,
    [normalized, nonce, expiresAt]
  );

  return nonce;
}

/**
 * Authenticate requests using Ethereum wallet signature.
 *
 * Expected headers:
 *   x-wallet-address: "0x..."
 *   x-signature: signed nonce message
 *   x-nonce: the nonce value
 */
async function authenticate(req, res, next) {
  try {
    const walletAddress = req.headers["x-wallet-address"];
    const signature = req.headers["x-signature"];
    const nonce = req.headers["x-nonce"];

    if (!walletAddress || !signature || !nonce) {
      return res.status(401).json({
        error: { code: "AUTH_REQUIRED", message: "Missing authentication headers (x-wallet-address, x-signature, x-nonce)" },
      });
    }

    // Validate address format
    if (!/^0x[a-fA-F0-9]{40}$/.test(walletAddress)) {
      return res.status(401).json({
        error: { code: "INVALID_ADDRESS", message: "Invalid wallet address format" },
      });
    }

    // Verify the signature matches the nonce message
    const message = `Sign this message to authenticate with Voting System.\nNonce: ${nonce}`;
    let recoveredAddress;
    try {
      recoveredAddress = blockchainService.verifySignature(message, signature);
    } catch (err) {
      return res.status(401).json({
        error: { code: "INVALID_SIGNATURE", message: "Could not recover address from signature" },
      });
    }

    if (recoveredAddress.toLowerCase() !== walletAddress.toLowerCase()) {
      return res.status(401).json({
        error: { code: "SIGNATURE_MISMATCH", message: "Signature does not match wallet address" },
      });
    }

    const normalized = walletAddress.toLowerCase();

    // Verify nonce exists, is not expired, and has not been used
    const nonceResult = await db.query(
      `SELECT id, expires_at, used FROM auth_nonces
       WHERE wallet_address = $1 AND nonce = $2
       ORDER BY created_at DESC LIMIT 1`,
      [normalized, nonce]
    );

    if (nonceResult.rows.length === 0) {
      return res.status(401).json({
        error: { code: "INVALID_NONCE", message: "Nonce not found" },
      });
    }

    const nonceRecord = nonceResult.rows[0];

    if (nonceRecord.used) {
      return res.status(401).json({
        error: { code: "NONCE_USED", message: "Nonce has already been used" },
      });
    }

    if (new Date(nonceRecord.expires_at) < new Date()) {
      return res.status(401).json({
        error: { code: "NONCE_EXPIRED", message: "Nonce has expired, request a new one" },
      });
    }

    // Mark nonce as used
    await db.query("UPDATE auth_nonces SET used = TRUE WHERE id = $1", [nonceRecord.id]);

    req.user = { walletAddress: normalized };
    next();
  } catch (err) {
    logger.error("Auth middleware error:", err);
    return res.status(401).json({
      error: { code: "AUTH_FAILED", message: "Authentication failed" },
    });
  }
}

/**
 * Optional auth — sets req.user if auth headers present, but doesn't block.
 */
async function optionalAuth(req, _res, next) {
  const walletAddress = req.headers["x-wallet-address"];
  if (walletAddress && /^0x[a-fA-F0-9]{40}$/.test(walletAddress)) {
    req.user = { walletAddress: walletAddress.toLowerCase() };
  }
  next();
}

module.exports = { authenticate, optionalAuth, generateNonce };
