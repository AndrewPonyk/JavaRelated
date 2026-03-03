// Web3.js v4 returns BigInt values which JSON.stringify cannot serialize.
// This polyfill ensures BigInts are converted to strings during serialization.
BigInt.prototype.toJSON = function () {
  return this.toString();
};

const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const morgan = require("morgan");
const crypto = require("crypto");
const routes = require("./routes");
const { errorHandler } = require("./middleware/errorHandler");
const blockchainService = require("./services/blockchain.service");
const voteService = require("./services/vote.service");
const logger = require("./utils/logger");

const app = express();
const PORT = process.env.PORT || 3001;

// --- Middleware ---
app.use(helmet());
app.use(cors({ origin: process.env.CORS_ORIGIN || "http://localhost:3000" }));
app.use(express.json({ limit: "1mb" }));

// Correlation ID for request tracing
app.use((req, _res, next) => {
  req.correlationId = req.headers["x-correlation-id"] || crypto.randomUUID();
  next();
});

// Request logging with correlation ID
app.use(morgan(":method :url :status :response-time ms :req[x-correlation-id]", {
  stream: { write: (msg) => logger.info(msg.trim()) },
}));

// Simple rate limiting (in-memory, use Redis in production)
const rateLimitMap = new Map();
const RATE_LIMIT_WINDOW = 60 * 1000; // 1 minute
const RATE_LIMIT_MAX = 100; // requests per window

app.use((req, res, next) => {
  const key = req.ip;
  const now = Date.now();
  const entry = rateLimitMap.get(key);

  if (!entry || now - entry.start > RATE_LIMIT_WINDOW) {
    rateLimitMap.set(key, { start: now, count: 1 });
    return next();
  }

  entry.count++;
  if (entry.count > RATE_LIMIT_MAX) {
    return res.status(429).json({
      error: { code: "RATE_LIMITED", message: "Too many requests, please try again later" },
    });
  }
  next();
});

// Clean up rate limit map periodically
setInterval(() => {
  const now = Date.now();
  for (const [key, entry] of rateLimitMap) {
    if (now - entry.start > RATE_LIMIT_WINDOW) {
      rateLimitMap.delete(key);
    }
  }
}, RATE_LIMIT_WINDOW);

// --- Routes ---
app.use("/api", routes);

// Health check
app.get("/health", async (_req, res) => {
  const bcStatus = blockchainService.getStatus();
  res.json({
    status: "ok",
    timestamp: new Date().toISOString(),
    blockchain: bcStatus,
  });
});

// --- Error Handling ---
app.use(errorHandler);

// --- Start ---
/**
 * Detect if the Hardhat node was restarted (chain state wiped) and reset
 * stale proposals that reference on-chain data that no longer exists.
 */
async function resetStaleChainData() {
  const db = require("./utils/db");
  try {
    // Check if VotingSystem contract has any proposals
    const onChainProposal = await blockchainService.getOnChainProposal(1);
    const chainHasProposals = onChainProposal && Number(onChainProposal.id) > 0;

    // Find DB proposals that claim to be on-chain
    const staleResult = await db.query(
      "SELECT id, chain_proposal_id, phase FROM proposals WHERE chain_proposal_id IS NOT NULL AND phase IN ('commit', 'reveal')"
    );

    if (staleResult.rows.length > 0 && !chainHasProposals) {
      // Chain was wiped but DB still has on-chain proposals — reset them
      logger.warn(`Detected ${staleResult.rows.length} stale on-chain proposals after chain reset. Resetting to draft.`);
      await db.query(
        "UPDATE proposals SET phase = 'draft', chain_proposal_id = NULL, commit_deadline = NULL, reveal_deadline = NULL, updated_at = NOW() WHERE chain_proposal_id IS NOT NULL AND phase IN ('commit', 'reveal')"
      );
      logger.info("Stale proposals reset to draft phase.");
    } else if (staleResult.rows.length > 0 && chainHasProposals) {
      // Chain has proposals — verify each one still exists
      for (const row of staleResult.rows) {
        const chainData = await blockchainService.getOnChainProposal(row.chain_proposal_id);
        if (!chainData || Number(chainData.id) === 0) {
          logger.warn(`Proposal ${row.id} (chain_id=${row.chain_proposal_id}) not found on-chain. Resetting to draft.`);
          await db.query(
            "UPDATE proposals SET phase = 'draft', chain_proposal_id = NULL, commit_deadline = NULL, reveal_deadline = NULL, updated_at = NOW() WHERE id = $1",
            [row.id]
          );
        }
      }
    }
  } catch (err) {
    logger.warn(`Could not verify chain state on startup: ${err.message}`);
  }
}

async function start() {
  try {
    // Initialize blockchain connection
    blockchainService.initialize();

    // Detect and reset stale chain data from previous Hardhat runs
    await resetStaleChainData();

    // Start event listeners to sync on-chain events to PostgreSQL
    blockchainService.startEventListener({
      onVoteCommitted: (event) => voteService.handleVoteCommittedEvent(event),
      onVoteRevealed: (event) => voteService.handleVoteRevealedEvent(event),
    });

    app.listen(PORT, () => {
      logger.info(`Voting System API running on port ${PORT}`);
    });
  } catch (err) {
    logger.error("Failed to start server:", err);
    process.exit(1);
  }
}

if (require.main === module) {
  start();
}

module.exports = app;
