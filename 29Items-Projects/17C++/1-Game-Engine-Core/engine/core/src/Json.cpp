#include "engine/core/Json.hpp"

#include <array>
#include <cmath>
#include <cstdio>
#include <sstream>

/// @file Json.cpp
/// @brief RFC 8259 JSON parser + serializer (recursive descent, no dependencies).

namespace engine {

const Json Json::kNull{};

// ---------------------------------------------------------------------------
// Accessors
// ---------------------------------------------------------------------------
bool Json::asBool(bool def) const {
    if (const auto* b = std::get_if<bool>(&value_)) {
        return *b;
    }
    return def;
}
double Json::asNumber(double def) const {
    if (const auto* d = std::get_if<double>(&value_)) {
        return *d;
    }
    return def;
}
i64 Json::asInt(i64 def) const {
    if (const auto* d = std::get_if<double>(&value_)) {
        return static_cast<i64>(*d);
    }
    return def;
}
f32 Json::asFloat(f32 def) const {
    if (const auto* d = std::get_if<double>(&value_)) {
        return static_cast<f32>(*d);
    }
    return def;
}
std::string Json::asString(const std::string& def) const {
    if (const auto* s = std::get_if<std::string>(&value_)) {
        return *s;
    }
    return def;
}

const Json::Array& Json::arr() const {
    static const Array empty;
    if (const auto* a = std::get_if<Array>(&value_)) {
        return *a;
    }
    return empty;
}
const Json::Object& Json::obj() const {
    static const Object empty;
    if (const auto* o = std::get_if<Object>(&value_)) {
        return *o;
    }
    return empty;
}

bool Json::contains(const std::string& key) const {
    const auto* o = std::get_if<Object>(&value_);
    return o != nullptr && o->find(key) != o->end();
}
const Json& Json::at(const std::string& key) const {
    if (const auto* o = std::get_if<Object>(&value_)) {
        if (const auto it = o->find(key); it != o->end()) {
            return it->second;
        }
    }
    return kNull;
}
Json& Json::operator[](const std::string& key) {
    if (!std::holds_alternative<Object>(value_)) {
        value_ = Object{};
    }
    return std::get<Object>(value_)[key];
}

void Json::push_back(Json v) {
    if (!std::holds_alternative<Array>(value_)) {
        value_ = Array{};
    }
    std::get<Array>(value_).push_back(std::move(v));
}
const Json& Json::operator[](usize index) const {
    if (const auto* a = std::get_if<Array>(&value_)) {
        if (index < a->size()) {
            return (*a)[index];
        }
    }
    return kNull;
}
usize Json::size() const {
    if (const auto* a = std::get_if<Array>(&value_)) {
        return a->size();
    }
    if (const auto* o = std::get_if<Object>(&value_)) {
        return o->size();
    }
    return 0;
}

// ---------------------------------------------------------------------------
// Serialization
// ---------------------------------------------------------------------------
namespace {
void escapeTo(std::string& out, const std::string& s) {
    out += '"';
    for (const char c : s) {
        switch (c) {
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\t': out += "\\t"; break;
            case '\r': out += "\\r"; break;
            case '\b': out += "\\b"; break;
            case '\f': out += "\\f"; break;
            default:
                if (static_cast<unsigned char>(c) < 0x20) {
                    std::array<char, 8> buf{};
                    std::snprintf(buf.data(), buf.size(), "\\u%04x", c);
                    out += buf.data();
                } else {
                    out += c;
                }
        }
    }
    out += '"';
}

void numberTo(std::string& out, double d) {
    if (std::isfinite(d) && d == std::floor(d) && std::abs(d) < 1e15) {
        out += std::to_string(static_cast<i64>(d)); // integral -> no decimal point
    } else {
        std::ostringstream oss;
        oss.precision(17);
        oss << d;
        out += oss.str();
    }
}
} // namespace

void Json::dumpTo(std::string& out, int indent, int depth) const {
    const bool pretty = indent > 0;
    const auto newlineIndent = [&](int d) {
        if (pretty) {
            out += '\n';
            out.append(static_cast<usize>(indent) * static_cast<usize>(d), ' ');
        }
    };

    std::visit(
        [&](const auto& v) {
            using T = std::decay_t<decltype(v)>;
            if constexpr (std::is_same_v<T, std::nullptr_t>) {
                out += "null";
            } else if constexpr (std::is_same_v<T, bool>) {
                out += v ? "true" : "false";
            } else if constexpr (std::is_same_v<T, double>) {
                numberTo(out, v);
            } else if constexpr (std::is_same_v<T, std::string>) {
                escapeTo(out, v);
            } else if constexpr (std::is_same_v<T, Array>) {
                if (v.empty()) {
                    out += "[]";
                    return;
                }
                out += '[';
                for (usize i = 0; i < v.size(); ++i) {
                    newlineIndent(depth + 1);
                    v[i].dumpTo(out, indent, depth + 1);
                    if (i + 1 < v.size()) {
                        out += ',';
                    }
                }
                newlineIndent(depth);
                out += ']';
            } else if constexpr (std::is_same_v<T, Object>) {
                if (v.empty()) {
                    out += "{}";
                    return;
                }
                out += '{';
                usize i = 0;
                for (const auto& [key, val] : v) {
                    newlineIndent(depth + 1);
                    escapeTo(out, key);
                    out += pretty ? ": " : ":";
                    val.dumpTo(out, indent, depth + 1);
                    if (++i < v.size()) {
                        out += ',';
                    }
                }
                newlineIndent(depth);
                out += '}';
            }
        },
        value_);
}

std::string Json::dump(int indent) const {
    std::string out;
    dumpTo(out, indent, 0);
    return out;
}

// ---------------------------------------------------------------------------
// Parsing
// ---------------------------------------------------------------------------
namespace {
class Parser {
public:
    explicit Parser(std::string_view text) : s_(text) {}

    Result<Json> parse() {
        skipWs();
        auto v = parseValue();
        if (!v) {
            return v;
        }
        skipWs();
        if (pos_ != s_.size()) {
            return fail("trailing characters after JSON value");
        }
        return v;
    }

private:
    Result<Json> fail(const std::string& msg) {
        return err<Json>(ErrorCode::DeserializeError,
                         "JSON parse error at offset " + std::to_string(pos_) + ": " + msg);
    }

    void skipWs() {
        while (pos_ < s_.size()) {
            const char c = s_[pos_];
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                ++pos_;
            } else {
                break;
            }
        }
    }

    // RAII nesting-depth guard: bounds recursion so hostile deeply-nested input
    // cannot exhaust the stack (untrusted save/asset files — see SECURITY §2.5).
    struct DepthGuard {
        int& depth;
        explicit DepthGuard(int& d) : depth(d) { ++depth; }
        ~DepthGuard() { --depth; }
    };

    Result<Json> parseValue() {
        DepthGuard guard(depth_);
        if (depth_ > kMaxDepth) {
            return fail("maximum nesting depth exceeded");
        }
        if (pos_ >= s_.size()) {
            return fail("unexpected end of input");
        }
        switch (s_[pos_]) {
            case '{': return parseObject();
            case '[': return parseArray();
            case '"': return parseStringValue();
            case 't': case 'f': return parseBool();
            case 'n': return parseNull();
            default:  return parseNumber();
        }
    }

    Result<Json> parseObject() {
        Json::Object obj;
        ++pos_; // consume '{'
        skipWs();
        if (pos_ < s_.size() && s_[pos_] == '}') {
            ++pos_;
            return Json(std::move(obj));
        }
        for (;;) {
            skipWs();
            if (pos_ >= s_.size() || s_[pos_] != '"') {
                return fail("expected string key");
            }
            auto key = parseString();
            if (!key) {
                return key.error();
            }
            skipWs();
            if (pos_ >= s_.size() || s_[pos_] != ':') {
                return fail("expected ':'");
            }
            ++pos_;
            skipWs();
            auto val = parseValue();
            if (!val) {
                return val;
            }
            obj.emplace(std::move(key.value()), std::move(val.value()));
            skipWs();
            if (pos_ >= s_.size()) {
                return fail("unterminated object");
            }
            if (s_[pos_] == ',') {
                ++pos_;
                continue;
            }
            if (s_[pos_] == '}') {
                ++pos_;
                return Json(std::move(obj));
            }
            return fail("expected ',' or '}'");
        }
    }

    Result<Json> parseArray() {
        Json::Array arr;
        ++pos_; // consume '['
        skipWs();
        if (pos_ < s_.size() && s_[pos_] == ']') {
            ++pos_;
            return Json(std::move(arr));
        }
        for (;;) {
            skipWs();
            auto val = parseValue();
            if (!val) {
                return val;
            }
            arr.push_back(std::move(val.value()));
            skipWs();
            if (pos_ >= s_.size()) {
                return fail("unterminated array");
            }
            if (s_[pos_] == ',') {
                ++pos_;
                continue;
            }
            if (s_[pos_] == ']') {
                ++pos_;
                return Json(std::move(arr));
            }
            return fail("expected ',' or ']'");
        }
    }

    Result<std::string> parseString() {
        ++pos_; // consume opening quote
        std::string out;
        while (pos_ < s_.size()) {
            const char c = s_[pos_++];
            if (c == '"') {
                return ok(std::move(out));
            }
            if (c == '\\') {
                if (pos_ >= s_.size()) {
                    break;
                }
                const char e = s_[pos_++];
                switch (e) {
                    case '"':  out += '"'; break;
                    case '\\': out += '\\'; break;
                    case '/':  out += '/'; break;
                    case 'n':  out += '\n'; break;
                    case 't':  out += '\t'; break;
                    case 'r':  out += '\r'; break;
                    case 'b':  out += '\b'; break;
                    case 'f':  out += '\f'; break;
                    case 'u': {
                        if (pos_ + 4 > s_.size()) {
                            return err<std::string>(ErrorCode::DeserializeError, "bad \\u escape");
                        }
                        unsigned cp = 0;
                        for (int i = 0; i < 4; ++i) {
                            cp = cp * 16 + hexVal(s_[pos_++]);
                        }
                        appendUtf8(out, cp); // BMP only (sufficient for engine data)
                        break;
                    }
                    default:
                        return err<std::string>(ErrorCode::DeserializeError, "bad escape char");
                }
            } else {
                out += c;
            }
        }
        return err<std::string>(ErrorCode::DeserializeError, "unterminated string");
    }

    Result<Json> parseStringValue() {
        auto s = parseString();
        if (!s) {
            return s.error();
        }
        return Json(std::move(s.value()));
    }

    Result<Json> parseBool() {
        if (s_.compare(pos_, 4, "true") == 0) {
            pos_ += 4;
            return Json(true);
        }
        if (s_.compare(pos_, 5, "false") == 0) {
            pos_ += 5;
            return Json(false);
        }
        return fail("invalid literal");
    }

    Result<Json> parseNull() {
        if (s_.compare(pos_, 4, "null") == 0) {
            pos_ += 4;
            return Json(nullptr);
        }
        return fail("invalid literal");
    }

    Result<Json> parseNumber() {
        const usize start = pos_;
        if (pos_ < s_.size() && (s_[pos_] == '-' || s_[pos_] == '+')) {
            ++pos_;
        }
        bool any = false;
        while (pos_ < s_.size() && (std::isdigit(static_cast<unsigned char>(s_[pos_])) != 0)) {
            ++pos_;
            any = true;
        }
        if (pos_ < s_.size() && s_[pos_] == '.') {
            ++pos_;
            while (pos_ < s_.size() && (std::isdigit(static_cast<unsigned char>(s_[pos_])) != 0)) {
                ++pos_;
                any = true;
            }
        }
        if (pos_ < s_.size() && (s_[pos_] == 'e' || s_[pos_] == 'E')) {
            ++pos_;
            if (pos_ < s_.size() && (s_[pos_] == '-' || s_[pos_] == '+')) {
                ++pos_;
            }
            while (pos_ < s_.size() && (std::isdigit(static_cast<unsigned char>(s_[pos_])) != 0)) {
                ++pos_;
            }
        }
        if (!any) {
            return fail("invalid number");
        }
        const std::string num(s_.substr(start, pos_ - start));
        try {
            return Json(std::stod(num));
        } catch (const std::exception&) {
            return fail("number out of range");
        }
    }

    static unsigned hexVal(char c) {
        if (c >= '0' && c <= '9') {
            return static_cast<unsigned>(c - '0');
        }
        if (c >= 'a' && c <= 'f') {
            return static_cast<unsigned>(c - 'a' + 10);
        }
        if (c >= 'A' && c <= 'F') {
            return static_cast<unsigned>(c - 'A' + 10);
        }
        return 0;
    }

    static void appendUtf8(std::string& out, unsigned cp) {
        if (cp < 0x80) {
            out += static_cast<char>(cp);
        } else if (cp < 0x800) {
            out += static_cast<char>(0xC0 | (cp >> 6));
            out += static_cast<char>(0x80 | (cp & 0x3F));
        } else {
            out += static_cast<char>(0xE0 | (cp >> 12));
            out += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (cp & 0x3F));
        }
    }

    static constexpr int kMaxDepth = 256;

    std::string_view s_;
    usize            pos_   = 0;
    int              depth_ = 0;
};
} // namespace

Result<Json> Json::parse(std::string_view text) {
    Parser parser(text);
    return parser.parse();
}

} // namespace engine
