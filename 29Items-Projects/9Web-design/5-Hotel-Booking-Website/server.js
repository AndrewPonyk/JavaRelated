const path = require("node:path");
const compression = require("compression");
const express = require("express");
const helmet = require("helmet");
require("dotenv").config();

const app = express();
const port = Number(process.env.PORT || 8888);

const functions = {
  availability: require("./netlify/functions/availability").handler,
  bookings: require("./netlify/functions/bookings").handler,
  guests: require("./netlify/functions/guests").handler,
  hotels: require("./netlify/functions/hotels").handler,
  rooms: require("./netlify/functions/rooms").handler,
  "staff-users": require("./netlify/functions/staff-users").handler,
};

app.use(
  helmet({
    contentSecurityPolicy: {
      directives: {
        defaultSrc: ["'self'"],
        connectSrc: ["'self'"],
        imgSrc: ["'self'", "data:", "https://images.unsplash.com", "https://*.tile.openstreetmap.org"],
        scriptSrc: ["'self'", "https://cdn.jsdelivr.net"],
        styleSrc: ["'self'", "https://cdn.jsdelivr.net", "'unsafe-inline'"],
        fontSrc: ["'self'", "https://cdn.jsdelivr.net", "data:"],
      },
    },
  }),
);
app.use(compression());
app.use(express.json({ limit: "64kb" }));
app.use(express.static(path.join(__dirname, "src")));

app.get("/health", (_req, res) => {
  res.json({ status: "ok" });
});

async function invokeFunction(name, req, res) {
  const handler = functions[name];
  const event = {
    httpMethod: req.method,
    headers: req.headers,
    queryStringParameters: req.query,
    body: ["GET", "HEAD"].includes(req.method) ? null : JSON.stringify(req.body || {}),
  };
  const result = await handler(event);

  Object.entries(result.headers || {}).forEach(([key, value]) => res.setHeader(key, value));
  res.status(result.statusCode).send(result.body);
}

for (const functionName of Object.keys(functions)) {
  app.all(`/api/${functionName}`, (req, res, next) => {
    invokeFunction(functionName, req, res).catch(next);
  });
  app.all(`/.netlify/functions/${functionName}`, (req, res, next) => {
    invokeFunction(functionName, req, res).catch(next);
  });
}

app.use((error, _req, res, _next) => {
  console.error("server.unhandled_error", error);
  res.status(500).json({ error: { code: "INTERNAL_ERROR", message: "Request failed." } });
});

app.listen(port, "0.0.0.0", () => {
  console.warn(`Hotel Booking Website listening on http://0.0.0.0:${port}`);
});
