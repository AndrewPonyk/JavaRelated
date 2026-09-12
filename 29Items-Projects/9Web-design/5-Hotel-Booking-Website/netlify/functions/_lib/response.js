function jsonResponse(statusCode, body) {
  return {
    statusCode,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
    },
    body: JSON.stringify(body),
  };
}

function errorResponse(statusCode, code, message) {
  return jsonResponse(statusCode, {
    error: {
      code,
      message,
    },
  });
}

function handleError(error, fallbackMessage = "Request failed.") {
  if (error.statusCode && error.code) {
    return errorResponse(error.statusCode, error.code, error.message);
  }

  console.error("api.unhandled_error", {
    message: error.message,
    stack: process.env.APP_ENV === "development" ? error.stack : undefined,
  });

  return errorResponse(500, "INTERNAL_ERROR", fallbackMessage);
}

function parseJsonBody(event) {
  if (!event.body) {
    return {};
  }

  try {
    return JSON.parse(event.body);
  } catch {
    const { badRequest } = require("./errors");
    throw badRequest("Request body must be valid JSON.");
  }
}

module.exports = {
  errorResponse,
  handleError,
  jsonResponse,
  parseJsonBody,
};
