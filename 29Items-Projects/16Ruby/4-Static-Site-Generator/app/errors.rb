# frozen_string_literal: true

class AppError < StandardError
  attr_reader :status, :code, :details

  def initialize(message, status: 500, code: "internal_error", details: nil)
    super(message)
    @status = status
    @code = code
    @details = details
  end

  def to_h
    payload = { error: code, message: message }
    payload[:details] = details if details
    payload
  end
end

class ValidationError < AppError
  def initialize(message = "Validation failed", details: [])
    super(message, status: 422, code: "validation_failed", details: details)
  end
end

class NotFoundError < AppError
  def initialize(message = "Resource not found")
    super(message, status: 404, code: "not_found")
  end
end

class ConflictError < AppError
  def initialize(message = "Resource already exists")
    super(message, status: 409, code: "conflict")
  end
end

class BadRequestError < AppError
  def initialize(message = "Bad request", details: nil)
    super(message, status: 400, code: "bad_request", details: details)
  end
end
