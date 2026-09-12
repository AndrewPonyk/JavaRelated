// ============================================================================
//  fix/FixMessage.hpp
//  Lightweight FIX (Financial Information eXchange) tag/value container.
//
//  FIX wire format is `tag=value<SOH>` repeated, where SOH is 0x01. Common
//  tags are interned as constants. The container holds field views into the
//  underlying receive buffer (zero-copy) for inbound, and a small builder for
//  outbound. POD-friendly so it can ride the disruptor.
// ============================================================================
#pragma once

#include <array>
#include <charconv>
#include <cstdint>
#include <optional>
#include <string_view>

namespace rts::fix {

constexpr char SOH = '\x01';

// A subset of standard FIX tags used by this engine.
namespace tag {
constexpr int BeginString   = 8;
constexpr int BodyLength    = 9;
constexpr int MsgType       = 35;
constexpr int SenderCompID  = 49;
constexpr int TargetCompID  = 56;
constexpr int MsgSeqNum     = 34;
constexpr int SendingTime   = 52;
constexpr int CheckSum      = 10;
constexpr int ClOrdID       = 11;
constexpr int OrderID       = 37;
constexpr int ExecID        = 17;
constexpr int Symbol        = 55;
constexpr int Side          = 54;
constexpr int OrderQty      = 38;
constexpr int OrdType       = 40;
constexpr int Price         = 44;
constexpr int TimeInForce   = 59;
constexpr int ExecType      = 150;
constexpr int OrdStatus     = 39;
constexpr int LastQty       = 32;
constexpr int LastPx        = 31;
constexpr int CumQty        = 14;
constexpr int LeavesQty     = 151;
constexpr int Text          = 58;
constexpr int TestReqID     = 112;
constexpr int HeartBtInt    = 108;
constexpr int EncryptMethod = 98;
}  // namespace tag

// MsgType values (tag 35).
namespace msgtype {
constexpr std::string_view Logon            = "A";
constexpr std::string_view Heartbeat        = "0";
constexpr std::string_view TestRequest      = "1";
constexpr std::string_view ResendRequest    = "2";
constexpr std::string_view Reject           = "3";
constexpr std::string_view SequenceReset    = "4";
constexpr std::string_view Logout           = "5";
constexpr std::string_view NewOrderSingle   = "D";
constexpr std::string_view OrderCancelReq   = "F";
constexpr std::string_view ExecutionReport  = "8";
}  // namespace msgtype

// Inbound view: parsed fields point into the source buffer (no copies).
class FixMessageView {
public:
    static constexpr std::size_t kMaxFields = 128;

    struct Field { int tag; std::string_view value; };

    void add(int t, std::string_view v) noexcept {
        if (count_ < kMaxFields) fields_[count_++] = {t, v};
    }

    [[nodiscard]] std::optional<std::string_view> get(int t) const noexcept {
        for (std::size_t i = 0; i < count_; ++i)
            if (fields_[i].tag == t) return fields_[i].value;
        return std::nullopt;
    }

    // Typed accessor for integer fields.
    [[nodiscard]] std::optional<std::int64_t> getInt(int t) const noexcept {
        auto v = get(t);
        if (!v) return std::nullopt;
        std::int64_t out{};
        auto [p, ec] = std::from_chars(v->data(), v->data() + v->size(), out);
        if (ec != std::errc{}) return std::nullopt;
        return out;
    }

    [[nodiscard]] std::string_view msgType() const noexcept {
        return get(tag::MsgType).value_or(std::string_view{});
    }
    [[nodiscard]] std::size_t size() const noexcept { return count_; }

private:
    std::array<Field, kMaxFields> fields_{};
    std::size_t count_{0};
};

}  // namespace rts::fix
