class ApiError extends Error {
  constructor(statusCode, code, message, details) {
    super(message);
    this.name = "ApiError";
    this.statusCode = statusCode;
    this.code = code;
    this.details = details || [];
  }
}

function badRequest(message, details) {
  return new ApiError(400, "BAD_REQUEST", message, details);
}

function notFound(resource) {
  return new ApiError(404, "NOT_FOUND", `${resource} was not found.`);
}

function validationFailed(details) {
  return new ApiError(400, "VALIDATION_ERROR", "Please check the highlighted fields.", details);
}

module.exports = { ApiError, badRequest, notFound, validationFailed };
