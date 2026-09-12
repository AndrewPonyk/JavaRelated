class AppError extends Error {
  constructor(statusCode, code, message) {
    super(message);
    this.name = "AppError";
    this.statusCode = statusCode;
    this.code = code;
  }
}

function badRequest(message) {
  return new AppError(400, "VALIDATION_ERROR", message);
}

function unauthorized(message = "Staff authorization is required.") {
  return new AppError(401, "UNAUTHORIZED", message);
}

function forbidden(message = "This staff role cannot perform the requested action.") {
  return new AppError(403, "FORBIDDEN", message);
}

function notFound(entity) {
  return new AppError(404, "NOT_FOUND", `${entity} was not found.`);
}

function conflict(message) {
  return new AppError(409, "CONFLICT", message);
}

module.exports = {
  AppError,
  badRequest,
  conflict,
  forbidden,
  notFound,
  unauthorized,
};
