const { generateNonce } = require("../middleware/auth.middleware");
const db = require("../utils/db");

/**
 * POST /api/auth/nonce
 * Generate a new authentication nonce for a wallet address.
 */
async function requestNonce(req, res, next) {
  try {
    const { walletAddress } = req.body;

    if (!walletAddress || !/^0x[a-fA-F0-9]{40}$/.test(walletAddress)) {
      return res.status(400).json({
        error: { code: "INVALID_ADDRESS", message: "Valid Ethereum address required" },
      });
    }

    const nonce = await generateNonce(walletAddress);

    res.json({
      data: {
        nonce,
        message: `Sign this message to authenticate with Voting System.\nNonce: ${nonce}`,
      },
    });
  } catch (err) {
    next(err);
  }
}

/**
 * GET /api/auth/me
 * Get the authenticated user's profile.
 */
async function getProfile(req, res, next) {
  try {
    const result = await db.query(
      "SELECT id, wallet_address, display_name, avatar_ipfs_cid, created_at FROM users WHERE wallet_address = $1",
      [req.user.walletAddress]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({
        error: { code: "NOT_FOUND", message: "User not found" },
      });
    }

    res.json({ data: result.rows[0] });
  } catch (err) {
    next(err);
  }
}

/**
 * PUT /api/auth/me
 * Update the authenticated user's profile.
 */
async function updateProfile(req, res, next) {
  try {
    const { displayName } = req.body;

    const result = await db.query(
      `UPDATE users SET display_name = $1, updated_at = NOW()
       WHERE wallet_address = $2 RETURNING id, wallet_address, display_name, created_at`,
      [displayName, req.user.walletAddress]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({
        error: { code: "NOT_FOUND", message: "User not found" },
      });
    }

    res.json({ data: result.rows[0] });
  } catch (err) {
    next(err);
  }
}

module.exports = { requestNonce, getProfile, updateProfile };
