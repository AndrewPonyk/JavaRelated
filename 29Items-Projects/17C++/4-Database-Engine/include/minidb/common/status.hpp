#pragma once

#include <cassert>
#include <optional>
#include <string>
#include <utility>

/// @file status.hpp
/// Value-based error handling. The storage and execution core is exception-free
/// on the hot path: expected failures are returned as `Status` / `StatusOr<T>`,
/// while broken invariants use `MINIDB_ASSERT`. See docs/ARCHITECTURE.md §2.6.

/// Active in debug builds; compiled out in release. Use for programmer-error
/// invariants (never for recoverable runtime conditions). Defined before first
/// use so it expands inside the inline member functions below.
#ifndef NDEBUG
#define MINIDB_ASSERT(cond, msg) assert((cond) && (msg))
#else
#define MINIDB_ASSERT(cond, msg) ((void)0)
#endif

namespace minidb {

enum class StatusCode {
  kOk = 0,
  kInvalidArgument,
  kNotFound,
  kAlreadyExists,
  kSyntaxError,
  kIOError,
  kOutOfMemory,
  kBufferPoolFull,
  kCorruption,
  kUnsupported,
  kInternal,
};

const char* StatusCodeToString(StatusCode code);

/// A lightweight, copyable result carrying a code and a human-readable message.
/// `[[nodiscard]]` so a forgotten error can't be silently dropped.
class [[nodiscard]] Status {
 public:
  Status() = default;  ///< Default-constructs an OK status.
  Status(StatusCode code, std::string message)
      : code_(code), message_(std::move(message)) {}

  static Status Ok() { return Status{}; }
  static Status InvalidArgument(std::string m) {
    return {StatusCode::kInvalidArgument, std::move(m)};
  }
  static Status NotFound(std::string m) {
    return {StatusCode::kNotFound, std::move(m)};
  }
  static Status AlreadyExists(std::string m) {
    return {StatusCode::kAlreadyExists, std::move(m)};
  }
  static Status SyntaxError(std::string m) {
    return {StatusCode::kSyntaxError, std::move(m)};
  }
  static Status IOError(std::string m) {
    return {StatusCode::kIOError, std::move(m)};
  }
  static Status OutOfMemory(std::string m) {
    return {StatusCode::kOutOfMemory, std::move(m)};
  }
  static Status BufferPoolFull(std::string m) {
    return {StatusCode::kBufferPoolFull, std::move(m)};
  }
  static Status Corruption(std::string m) {
    return {StatusCode::kCorruption, std::move(m)};
  }
  static Status Unsupported(std::string m) {
    return {StatusCode::kUnsupported, std::move(m)};
  }
  static Status Internal(std::string m) {
    return {StatusCode::kInternal, std::move(m)};
  }

  bool ok() const { return code_ == StatusCode::kOk; }
  StatusCode code() const { return code_; }
  const std::string& message() const { return message_; }

  /// Renders as "CODE: message" (or "OK").
  std::string ToString() const;

 private:
  StatusCode code_ = StatusCode::kOk;
  std::string message_;
};

/// A union of either a `Status` (error) or a value of type `T` (success).
/// Modeled after absl::StatusOr. Construct from a `T` for success or a non-OK
/// `Status` for failure.
template <typename T>
class [[nodiscard]] StatusOr {
 public:
  // Intentionally implicit so functions can `return value;` or `return
  // status;`.
  StatusOr(T value) : status_(Status::Ok()), value_(std::move(value)) {}
  StatusOr(Status status) : status_(std::move(status)) {
    MINIDB_ASSERT(!status_.ok(),
                  "StatusOr constructed from an OK Status carries no value");
  }

  bool ok() const { return status_.ok(); }
  const Status& status() const { return status_; }

  T& value() & { return *value_; }
  const T& value() const& { return *value_; }
  T&& value() && { return std::move(*value_); }

  T value_or(T fallback) const& { return ok() ? *value_ : std::move(fallback); }

  T* operator->() { return &*value_; }
  const T* operator->() const { return &*value_; }

 private:
  Status status_;
  std::optional<T> value_;
};

}  // namespace minidb

/// Early-return helper for functions returning `Status` or `StatusOr<U>`
/// (the latter via Status's implicit conversion).
#define MINIDB_RETURN_IF_ERROR(expr)       \
  do {                                     \
    ::minidb::Status _minidb_s = (expr);   \
    if (!_minidb_s.ok()) return _minidb_s; \
  } while (0)
