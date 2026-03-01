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
async function start() {
  try {
    // Initialize blockchain connection
    blockchainService.initialize();

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
