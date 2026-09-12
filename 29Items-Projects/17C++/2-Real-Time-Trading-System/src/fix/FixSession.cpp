// ============================================================================
//  fix/FixSession.cpp
//  Session-layer state machine implementation. Handles the FIX admin protocol
//  (logon, heartbeat, test request, gap detection, logout) and routes
//  application messages to the engine. Sequence numbers are persisted.
// ============================================================================
#include "fix/FixSession.hpp"

#include <chrono>
#include <cstdio>
#include <ctime>
#include <fstream>
#include <string>

#include "common/Logger.hpp"
#include "fix/FixParser.hpp"

namespace rts::fix {

std::string nowFixTimestamp() {
    using namespace std::chrono;
    const auto now = system_clock::now();
    const auto t   = system_clock::to_time_t(now);
    const auto ms  = duration_cast<milliseconds>(now.time_since_epoch()) % 1000;
    std::tm tm{};
#if defined(_WIN32)
    gmtime_s(&tm, &t);
#else
    gmtime_r(&t, &tm);
#endif
    char buf[48];  // ample for "YYYYMMDD-HH:MM:SS.mmm" (silences -Wformat-truncation)
    std::snprintf(buf, sizeof(buf), "%04d%02d%02d-%02d:%02d:%02d.%03d",
                  tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday, tm.tm_hour,
                  tm.tm_min, tm.tm_sec, static_cast<int>(ms.count()));
    return std::string(buf);
}

FixSession::FixSession(SessionConfig cfg, WireSink sink, AppMessageHandler onApp)
    : cfg_(std::move(cfg)), sink_(std::move(sink)), onApp_(std::move(onApp)) {
    loadSeqNums();
}

void FixSession::setState(SessionState s) {
    state_ = s;
    if (onState_) onState_(s);
}

void FixSession::connect() {
    // Set LogonSent *before* sending: with an in-process (synchronous) wire the
    // counterparty's Logon reply arrives nested inside sendLogon() and advances
    // the state to LoggedOn, which must not then be clobbered back.
    setState(SessionState::LogonSent);
    sendLogon();
}

void FixSession::disconnect() {
    if (state_ == SessionState::LoggedOn) sendLogout();
    setState(SessionState::LogoutSent);
}

std::uint64_t FixSession::claimSeq() noexcept {
    const std::uint64_t s = outSeq_++;
    persistSeqNums();
    return s;
}

void FixSession::sendBytes(std::string_view framed) {
    sink_(framed.data(), framed.size());
}

void FixSession::sendLogon() {
    FixBuilder b(cfg_.beginString);
    b.field(tag::MsgType, msgtype::Logon)
        .field(tag::SenderCompID, cfg_.senderCompId)
        .field(tag::TargetCompID, cfg_.targetCompId)
        .field(tag::MsgSeqNum, static_cast<std::int64_t>(claimSeq()))
        .field(tag::SendingTime, nowFixTimestamp())
        .field(tag::EncryptMethod, std::int64_t{0})
        .field(tag::HeartBtInt, static_cast<std::int64_t>(cfg_.heartbeatSecs));
    sendBytes(b.finish());
    RTS_INFO("FIX logon sent");
}

void FixSession::sendLogout() {
    FixBuilder b(cfg_.beginString);
    b.field(tag::MsgType, msgtype::Logout)
        .field(tag::SenderCompID, cfg_.senderCompId)
        .field(tag::TargetCompID, cfg_.targetCompId)
        .field(tag::MsgSeqNum, static_cast<std::int64_t>(claimSeq()))
        .field(tag::SendingTime, nowFixTimestamp());
    sendBytes(b.finish());
}

void FixSession::sendHeartbeat(std::string_view testReqId) {
    FixBuilder b(cfg_.beginString);
    b.field(tag::MsgType, msgtype::Heartbeat)
        .field(tag::SenderCompID, cfg_.senderCompId)
        .field(tag::TargetCompID, cfg_.targetCompId)
        .field(tag::MsgSeqNum, static_cast<std::int64_t>(claimSeq()))
        .field(tag::SendingTime, nowFixTimestamp());
    if (!testReqId.empty()) b.field(tag::TestReqID, testReqId);
    sendBytes(b.finish());
}

void FixSession::sendTestRequest() {
    FixBuilder b(cfg_.beginString);
    b.field(tag::MsgType, msgtype::TestRequest)
        .field(tag::SenderCompID, cfg_.senderCompId)
        .field(tag::TargetCompID, cfg_.targetCompId)
        .field(tag::MsgSeqNum, static_cast<std::int64_t>(claimSeq()))
        .field(tag::SendingTime, nowFixTimestamp())
        .field(tag::TestReqID, std::to_string(outSeq_));
    sendBytes(b.finish());
    awaitingPong_ = true;
}

void FixSession::sendResendRequest(std::uint64_t from, std::uint64_t to) {
    FixBuilder b(cfg_.beginString);
    b.field(tag::MsgType, msgtype::ResendRequest)
        .field(tag::SenderCompID, cfg_.senderCompId)
        .field(tag::TargetCompID, cfg_.targetCompId)
        .field(tag::MsgSeqNum, static_cast<std::int64_t>(claimSeq()))
        .field(tag::SendingTime, nowFixTimestamp())
        .field(7, static_cast<std::int64_t>(from))   // BeginSeqNo
        .field(16, static_cast<std::int64_t>(to));    // EndSeqNo
    sendBytes(b.finish());
    RTS_WARN("FIX gap detected; resend requested",
             static_cast<std::int64_t>(from), static_cast<std::int64_t>(to));
}

void FixSession::onBytes(const char* data, std::size_t len) {
    constexpr std::size_t kMaxRxBuffer = 1u << 20;  // 1 MiB cap (anti-DoS)
    rxBuffer_.append(data, len);

    FixMessageView msg;
    std::size_t consumed = 0;
    while (FixParser::parse(rxBuffer_, msg, consumed, /*verify=*/true)) {
        // Sequence handling.
        if (auto seqOpt = msg.getInt(tag::MsgSeqNum)) {
            const auto seq = static_cast<std::uint64_t>(*seqOpt);
            if (seq == inSeq_) {
                ++inSeq_;
            } else if (seq > inSeq_) {
                sendResendRequest(inSeq_, seq - 1);
                inSeq_ = seq + 1;  // sim: accept and advance (no store/replay)
            }
            // seq < inSeq_: possible duplicate -> ignore for admin, accept app.
        }

        const auto mt = msg.msgType();
        const bool admin =
            mt == msgtype::Logon || mt == msgtype::Heartbeat ||
            mt == msgtype::TestRequest || mt == msgtype::ResendRequest ||
            mt == msgtype::SequenceReset || mt == msgtype::Logout;
        if (admin) {
            handleAdmin(msg);
        } else {
            onApp_(msg);
        }

        rxBuffer_.erase(0, consumed);
        msg = FixMessageView{};
    }
    if (rxBuffer_.size() > kMaxRxBuffer) {
        RTS_ERROR("FIX rx buffer overflow without a parseable message; clearing",
                  static_cast<std::int64_t>(rxBuffer_.size()));
        rxBuffer_.clear();
    }
    persistSeqNums();
}

void FixSession::handleAdmin(const FixMessageView& msg) {
    const auto mt = msg.msgType();
    if (mt == msgtype::Logon) {
        setState(SessionState::LoggedOn);
        RTS_INFO("FIX session logged on");
    } else if (mt == msgtype::TestRequest) {
        sendHeartbeat(msg.get(tag::TestReqID).value_or(std::string_view{}));
    } else if (mt == msgtype::Heartbeat) {
        awaitingPong_ = false;
    } else if (mt == msgtype::Logout) {
        setState(SessionState::Disconnected);
    }
    // SequenceReset / ResendRequest: minimal handling for the in-process sim.
}

void FixSession::onTimer(std::int64_t nowMs) {
    if (state_ != SessionState::LoggedOn) return;
    const std::int64_t hbMs = static_cast<std::int64_t>(cfg_.heartbeatSecs) * 1000;
    if (nowMs - lastTxMs_ >= hbMs) {
        sendHeartbeat();
        lastTxMs_ = nowMs;
    }
    if (!awaitingPong_ && nowMs - lastRxMs_ >= 2 * hbMs) {
        sendTestRequest();
    }
}

void FixSession::loadSeqNums() {
    if (cfg_.storePath.empty()) return;
    std::ifstream in(cfg_.storePath);
    if (in) {
        std::uint64_t out = 1, inn = 1;
        in >> out >> inn;
        if (in) { outSeq_ = out; inSeq_ = inn; }
    }
}

void FixSession::persistSeqNums() {
    if (cfg_.storePath.empty()) return;
    std::ofstream out(cfg_.storePath, std::ios::trunc);
    if (out) out << outSeq_ << ' ' << inSeq_ << '\n';
}

}  // namespace rts::fix
