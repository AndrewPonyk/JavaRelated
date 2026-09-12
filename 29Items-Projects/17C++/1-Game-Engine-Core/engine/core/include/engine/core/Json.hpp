#pragma once

#include "engine/core/Result.hpp"
#include "engine/core/Types.hpp"

#include <initializer_list>
#include <map>
#include <string>
#include <string_view>
#include <utility>
#include <variant>
#include <vector>

/// @file Json.hpp
/// @brief Self-contained JSON value, parser, and serializer.
///
/// A real (RFC 8259) recursive-descent implementation so scene/asset serialization
/// works with zero third-party dependencies. nlohmann::json remains an optional drop-in
/// for performance-sensitive paths. Objects keep keys sorted for deterministic output.

namespace engine {

class Json {
public:
    using Array  = std::vector<Json>;
    using Object = std::map<std::string, Json>;

    Json() : value_(nullptr) {}
    Json(std::nullptr_t) : value_(nullptr) {}                 // NOLINT
    Json(bool b) : value_(b) {}                               // NOLINT
    Json(int i) : value_(static_cast<double>(i)) {}           // NOLINT
    Json(i64 i) : value_(static_cast<double>(i)) {}           // NOLINT
    Json(u32 i) : value_(static_cast<double>(i)) {}           // NOLINT
    Json(usize i) : value_(static_cast<double>(i)) {}         // NOLINT
    Json(double d) : value_(d) {}                             // NOLINT
    Json(float d) : value_(static_cast<double>(d)) {}         // NOLINT
    Json(const char* s) : value_(std::string(s)) {}           // NOLINT
    Json(std::string s) : value_(std::move(s)) {}             // NOLINT
    Json(Array a) : value_(std::move(a)) {}                   // NOLINT
    Json(Object o) : value_(std::move(o)) {}                  // NOLINT

    static Json array() { return Json(Array{}); }
    static Json object() { return Json(Object{}); }

    [[nodiscard]] bool isNull() const { return std::holds_alternative<std::nullptr_t>(value_); }
    [[nodiscard]] bool isBool() const { return std::holds_alternative<bool>(value_); }
    [[nodiscard]] bool isNumber() const { return std::holds_alternative<double>(value_); }
    [[nodiscard]] bool isString() const { return std::holds_alternative<std::string>(value_); }
    [[nodiscard]] bool isArray() const { return std::holds_alternative<Array>(value_); }
    [[nodiscard]] bool isObject() const { return std::holds_alternative<Object>(value_); }

    [[nodiscard]] bool   asBool(bool def = false) const;
    [[nodiscard]] double asNumber(double def = 0.0) const;
    [[nodiscard]] i64    asInt(i64 def = 0) const;
    [[nodiscard]] f32    asFloat(f32 def = 0.0f) const;
    [[nodiscard]] std::string asString(const std::string& def = {}) const;

    [[nodiscard]] const Array&  arr() const;
    [[nodiscard]] const Object& obj() const;

    // Object helpers.
    [[nodiscard]] bool        contains(const std::string& key) const;
    [[nodiscard]] const Json& at(const std::string& key) const; // kNull if absent
    Json&                     operator[](const std::string& key); // creates as object

    // Array helpers.
    void                      push_back(Json v);
    [[nodiscard]] const Json& operator[](usize index) const;     // kNull if OOB
    [[nodiscard]] usize       size() const;

    /// Serialize. `indent > 0` pretty-prints with that many spaces per level.
    [[nodiscard]] std::string dump(int indent = 0) const;

    /// Parse JSON text. Returns a descriptive error on malformed input.
    [[nodiscard]] static Result<Json> parse(std::string_view text);

    static const Json kNull;

private:
    void dumpTo(std::string& out, int indent, int depth) const;

    std::variant<std::nullptr_t, bool, double, std::string, Array, Object> value_;
};

} // namespace engine
