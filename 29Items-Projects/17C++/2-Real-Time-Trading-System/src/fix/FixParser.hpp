// ============================================================================
//  fix/FixParser.hpp
//  Complete FIX codec: zero-copy inbound parser + a framing message builder.
//
//   - FixParser::parse  splits one complete `tag=value<SOH>` message into views
//     over the receive buffer (no allocation, no copies), bounded and defensive.
//   - computeChecksum / validate  implement the FIX trailer (tag 10).
//   - FixBuilder  accumulates body fields, then finish() frames the message with
//     the BeginString(8) + BodyLength(9) header and the CheckSum(10) trailer.
// ============================================================================
#pragma once

#include <charconv>
#include <cstdint>
#include <cstdio>
#include <string>
#include <string_view>

#include "common/Types.hpp"
#include "fix/FixMessage.hpp"

namespace rts::fix {

// Modulo-256 sum of every byte in `s`. The FIX checksum is computed over the
// whole message up to (but not including) the "10=" checksum field.
[[nodiscard]] inline int computeChecksum(std::string_view s) noexcept {
    unsigned sum = 0;
    for (unsigned char c : s) sum += c;
    return static_cast<int>(sum % 256);
}

class FixParser {
public:
    // Parse one complete message from `buf`. On success returns true, fills
    // `out`, and sets `consumed` to the byte count of the message. Returns
    // false if the buffer does not yet hold a full message (read more) or the
    // message is malformed. If `verify` is set, the CheckSum must validate.
    static bool parse(std::string_view buf, FixMessageView& out,
                      std::size_t& consumed, bool verify = true) noexcept {
        out = FixMessageView{};
        std::size_t pos = 0;

        while (pos < buf.size()) {
            const std::size_t eq = buf.find('=', pos);
            if (eq == std::string_view::npos) return false;  // incomplete

            int t{};
            const auto* tagBegin = buf.data() + pos;
            const auto* tagEnd   = buf.data() + eq;
            if (std::from_chars(tagBegin, tagEnd, t).ec != std::errc{}) return false;

            const std::size_t soh = buf.find(SOH, eq + 1);
            if (soh == std::string_view::npos) return false;  // incomplete

            const std::string_view val = buf.substr(eq + 1, soh - eq - 1);
            out.add(t, val);
            const std::size_t nextPos = soh + 1;

            if (t == tag::CheckSum) {
                consumed = nextPos;
                if (verify) {
                    int got{};
                    if (std::from_chars(val.data(), val.data() + val.size(), got).ec
                        != std::errc{})
                        return false;
                    // Checksum covers everything before the "10=" field (= pos).
                    if (computeChecksum(buf.substr(0, pos)) != got) return false;
                }
                return true;
            }
            pos = nextPos;
        }
        return false;  // no checksum field yet -> incomplete
    }

    // Validate the trailer of a fully-framed message string.
    [[nodiscard]] static bool validate(std::string_view msg) noexcept {
        FixMessageView v;
        std::size_t consumed = 0;
        return parse(msg, v, consumed, /*verify=*/true) && consumed == msg.size();
    }
};

// ---- Builder: frames a complete, checksummed FIX message -------------------
class FixBuilder {
public:
    explicit FixBuilder(std::string_view beginString = "FIX.4.4")
        : beginString_(beginString) {}

    FixBuilder& field(int tag, std::string_view value) {
        appendTag(tag);
        body_ += value;
        body_ += SOH;
        return *this;
    }

    FixBuilder& field(int tag, std::int64_t value) {
        appendTag(tag);
        char tmp[24];
        auto [p, ec] = std::to_chars(tmp, tmp + sizeof(tmp), value);
        body_.append(tmp, static_cast<std::size_t>(p - tmp));
        body_ += SOH;
        return *this;
    }

    // Fixed-point price emitted with up to 9 decimals (trailing zeros trimmed).
    FixBuilder& priceField(int tag, Price px) {
        appendTag(tag);
        body_ += formatPrice(px);
        body_ += SOH;
        return *this;
    }

    // Returns the fully framed message: 8=..|9=..|<body>|10=..|
    [[nodiscard]] std::string finish() const {
        std::string header;
        header += "8=";
        header += beginString_;
        header += SOH;
        header += "9=";
        header += std::to_string(body_.size());
        header += SOH;

        std::string prefix = header + body_;
        const int cs = computeChecksum(prefix);

        char csbuf[4];
        std::snprintf(csbuf, sizeof(csbuf), "%03d", cs);
        prefix += "10=";
        prefix.append(csbuf, 3);
        prefix += SOH;
        return prefix;
    }

    [[nodiscard]] const std::string& body() const noexcept { return body_; }

private:
    void appendTag(int tag) {
        char tmp[12];
        auto [p, ec] = std::to_chars(tmp, tmp + sizeof(tmp), tag);
        body_.append(tmp, static_cast<std::size_t>(p - tmp));
        body_ += '=';
    }

    static std::string formatPrice(Price px) {
        // ticks are 1e-9; print as decimal with trimmed trailing zeros.
        const bool neg = px.ticks < 0;
        std::int64_t v = neg ? -px.ticks : px.ticks;
        std::int64_t whole = v / kPriceScale;
        std::int64_t frac  = v % kPriceScale;
        char fracbuf[10];
        std::snprintf(fracbuf, sizeof(fracbuf), "%09lld", static_cast<long long>(frac));
        std::string s = std::string(fracbuf);
        std::size_t last = s.find_last_not_of('0');
        std::string out = (neg ? "-" : "") + std::to_string(whole);
        if (last != std::string::npos) out += "." + s.substr(0, last + 1);
        return out;
    }

    std::string beginString_;
    std::string body_;
};

// Parse a FIX price string (e.g. "100.25") into fixed-point ticks.
[[nodiscard]] inline Price parsePrice(std::string_view s) noexcept {
    double d = 0.0;
    // from_chars for double is supported by MSVC/GCC13/Clang17.
    std::from_chars(s.data(), s.data() + s.size(), d);
    return Price::fromDouble(d);
}

}  // namespace rts::fix
