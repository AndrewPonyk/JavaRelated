// imgproc/core/status.hpp
// Lightweight value-based error type (absl::Status-style) used on hot paths
// to avoid throwing. Public-boundary code may convert Status -> exception.
#pragma once

#include <string>
#include <utility>

namespace imgproc::core {

enum class StatusCode : int {
    kOk = 0,
    kInvalidArgument,
    kNotFound,
    kUnsupported,
    kCudaError,
    kModelError,
    kIoError,
    kInternal,
};

/// A non-throwing result type carrying a code and a human-readable message.
class Status {
public:
    Status() = default;  // ok
    Status(StatusCode code, std::string message)
        : code_(code), message_(std::move(message)) {}

    static Status Ok() { return {}; }

    [[nodiscard]] bool ok() const noexcept { return code_ == StatusCode::kOk; }
    [[nodiscard]] StatusCode code() const noexcept { return code_; }
    [[nodiscard]] const std::string& message() const noexcept { return message_; }

    explicit operator bool() const noexcept { return ok(); }

private:
    StatusCode code_{StatusCode::kOk};
    std::string message_{};
};

/// Convenience factories.
inline Status InvalidArgument(std::string m) {
    return {StatusCode::kInvalidArgument, std::move(m)};
}
inline Status Unsupported(std::string m) {
    return {StatusCode::kUnsupported, std::move(m)};
}
inline Status CudaError(std::string m) {
    return {StatusCode::kCudaError, std::move(m)};
}

}  // namespace imgproc::core
