#pragma once

#include <string>
#include <utility>
#include <variant>

/// @file Result.hpp
/// @brief Vocabulary type for recoverable, expected failures.
///
/// Used instead of exceptions across module boundaries and in fallible operations
/// (asset load, device creation, parsing). Hot per-frame loops avoid even this and
/// assert invariants instead. See docs/ARCHITECTURE.md §2.6.

namespace engine {

/// Canonical engine error categories.
enum class ErrorCode {
    Unknown,
    NotFound,
    InvalidArgument,
    OutOfMemory,
    IoError,
    DeserializeError,
    BackendError, // Vulkan/SDL/etc. failure translated at the boundary
    Unsupported,
};

struct Error {
    ErrorCode   code = ErrorCode::Unknown;
    std::string message;

    Error() = default;
    Error(ErrorCode c, std::string msg) : code(c), message(std::move(msg)) {}
};

/// Minimal Result<T,E>. (A future revision may switch to std::expected once the
/// toolchain floor supports it everywhere.)
template <typename T, typename E = Error>
class [[nodiscard]] Result {
public:
    Result(T value) : storage_(std::move(value)) {}   // NOLINT(google-explicit-constructor)
    Result(E error) : storage_(std::move(error)) {}   // NOLINT(google-explicit-constructor)

    [[nodiscard]] bool hasValue() const noexcept { return std::holds_alternative<T>(storage_); }
    explicit operator bool() const noexcept { return hasValue(); }

    T&       value() & { return std::get<T>(storage_); }
    const T& value() const& { return std::get<T>(storage_); }
    T        valueOr(T fallback) const { return hasValue() ? std::get<T>(storage_) : fallback; }

    const E& error() const& { return std::get<E>(storage_); }

private:
    std::variant<T, E> storage_;
};

/// Convenience makers.
template <typename T>
Result<T> ok(T value) {
    return Result<T>(std::move(value));
}

template <typename T>
Result<T> err(ErrorCode code, std::string message) {
    return Result<T>(Error{code, std::move(message)});
}

} // namespace engine
