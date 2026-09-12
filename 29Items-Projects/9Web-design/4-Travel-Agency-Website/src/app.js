const path = require("node:path");
const express = require("express");
const helmet = require("helmet");
const cors = require("cors");
const compression = require("compression");
const morgan = require("morgan");
const { query } = require("./api/db/pool");
const { handleInquiryRequest } = require("./api/routes/inquiries.route");
const { handleTourRequest } = require("./api/routes/tours.route");
const { handleDestinationRequest } = require("./api/routes/destinations.route");

function createApp() {
  const app = express();

  app.set("trust proxy", 1);
  app.use(enforceHttps);
  app.use(compression());
  app.use(helmet({ contentSecurityPolicy: false }));
  app.use(cors({ origin: getCorsOrigin() }));
  app.use(express.json({ limit: "100kb" }));
  app.use(morgan(process.env.NODE_ENV === "test" ? "tiny" : "combined", { skip: () => process.env.NODE_ENV === "test" }));

  app.get("/health", healthCheck);
  app.all("/api/inquiries", adaptNetlifyHandler(handleInquiryRequest, "inquiries"));
  app.all("/api/inquiries/:id", adaptNetlifyHandler(handleInquiryRequest, "inquiries"));
  app.all("/api/tours", adaptNetlifyHandler(handleTourRequest, "tours"));
  app.all("/api/tours/:id", adaptNetlifyHandler(handleTourRequest, "tours"));
  app.all("/api/destinations", adaptNetlifyHandler(handleDestinationRequest, "destinations"));
  app.all("/api/destinations/:id", adaptNetlifyHandler(handleDestinationRequest, "destinations"));

  app.use(express.static(path.join(__dirname, "..")));
  app.use(jsonErrorHandler);

  return app;
}

function enforceHttps(req, res, next) {
  if (process.env.ENFORCE_HTTPS === "true" && req.headers["x-forwarded-proto"] === "http") {
    res.redirect(308, `https://${req.headers.host}${req.originalUrl}`);
    return;
  }

  next();
}

function getCorsOrigin() {
  if (!process.env.CORS_ORIGIN) {
    return false;
  }

  return process.env.CORS_ORIGIN.split(",").map((origin) => origin.trim()).filter(Boolean);
}

async function healthCheck(_req, res) {
  try {
    await query("SELECT 1");
    res.status(200).json({ status: "ok", database: "ok", uptime: process.uptime() });
  } catch (_error) {
    res.status(503).json({ status: "error", database: "unavailable" });
  }
}

function adaptNetlifyHandler(handler, name) {
  return async (req, res) => {
    const response = await handler({
      httpMethod: req.method,
      path: `/api/${name}${req.params.id ? `/${req.params.id}` : ""}`,
      queryStringParameters: req.query,
      body: ["GET", "HEAD"].includes(req.method) ? "" : JSON.stringify(req.body || {})
    });

    Object.entries(response.headers || {}).forEach(([key, value]) => res.setHeader(key, value));
    res.status(response.statusCode);

    if (response.body) {
      res.send(response.body);
    } else {
      res.end();
    }
  };
}

function jsonErrorHandler(error, _req, res, next) {
  if (!error) {
    next();
    return;
  }

  if (error instanceof SyntaxError && "body" in error) {
    res.status(400).json({
      error: {
        code: "BAD_JSON",
        message: "Request body must be valid JSON.",
        details: []
      }
    });
    return;
  }

  next(error);
}

module.exports = { createApp };
