const { ApiError } = require("../errors");

function json(statusCode, body) {
  return {
    statusCode,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": "no-store"
    },
    body: body === null ? "" : JSON.stringify(body)
  };
}

function parseJsonBody(body) {
  if (!body) {
    return {};
  }

  try {
    return JSON.parse(body);
  } catch (error) {
    const parseError = new ApiError(400, "BAD_JSON", "Request body must be valid JSON.");
    parseError.cause = error;
    throw parseError;
  }
}

function methodNotAllowed(methods) {
  return {
    statusCode: 405,
    headers: {
      Allow: methods.join(", "),
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      error: {
        code: "METHOD_NOT_ALLOWED",
        message: "HTTP method is not supported for this endpoint."
      }
    })
  };
}

function validationError(details) {
  return json(400, {
    error: {
      code: "VALIDATION_ERROR",
      message: "Please check the highlighted fields.",
      details
    }
  });
}

function errorResponse(error) {
  if (error instanceof ApiError) {
    return json(error.statusCode, {
      error: {
        code: error.code,
        message: error.message,
        details: error.details
      }
    });
  }

  console.error("unexpected_api_error", { message: error.message, stack: error.stack });
  return json(500, {
    error: {
      code: "INTERNAL_ERROR",
      message: "The service is unavailable. Please try again later.",
      details: []
    }
  });
}

function parseQuery(event) {
  return event.queryStringParameters || {};
}

function parsePagination(query) {
  const limit = clampInteger(query.limit, 20, 1, 100);
  const offset = clampInteger(query.offset, 0, 0, 100000);

  return { limit, offset };
}

function clampInteger(value, defaultValue, min, max) {
  if (value === undefined || value === "") {
    return defaultValue;
  }

  const parsed = Number(value);

  if (!Number.isInteger(parsed)) {
    return defaultValue;
  }

  return Math.min(Math.max(parsed, min), max);
}

function parsePathId(event, functionName) {
  const path = event.path || "";
  const parts = path.split("/").filter(Boolean);
  const apiIndex = parts.findIndex((part) => part === functionName || part === `${functionName}.js`);

  if (apiIndex >= 0 && parts[apiIndex + 1]) {
    return parts[apiIndex + 1];
  }

  const namedIndex = parts.findIndex((part) => part === "api");
  if (namedIndex >= 0 && parts[namedIndex + 2]) {
    return parts[namedIndex + 2];
  }

  return undefined;
}

module.exports = {
  errorResponse,
  json,
  methodNotAllowed,
  parseJsonBody,
  parsePagination,
  parsePathId,
  parseQuery,
  validationError
};
