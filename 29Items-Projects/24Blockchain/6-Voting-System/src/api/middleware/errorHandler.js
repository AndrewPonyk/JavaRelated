const logger = require("../utils/logger");

/**
 * Global error handler middleware.
 * Catches unhandled errors and returns consistent JSON responses.
 */
function errorHandler(err, req, res, _next) {
  const correlationId = req.correlationId || req.headers["x-correlation-id"] || "unknown";

  logger.error({
    message: err.message,
    stack: err.stack,
    correlationId,
    method: req.method,
    path: req.path,
  });

  // Determine status code
  const statusCode = err.statusCode || err.status || 500;

  // Don't leak internal error details in production
  const message =
    process.env.NODE_ENV === "production" && statusCode === 500
      ? "Internal server error"
      : err.message;

  res.status(statusCode).json({
    error: {
      code: err.code || "INTERNAL_ERROR",
      message,
      correlationId,
    },
  });
}

module.exports = { errorHandler };
