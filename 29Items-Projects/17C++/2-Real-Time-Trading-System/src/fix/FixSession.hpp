// ============================================================================
//  fix/FixSession.hpp
//  FIX session-layer state machine: logon, sequence numbers, heartbeats, test
//  requests, gap detection, logout. Application messages (orders / execution
//  reports) are routed to a callback once the session layer accepts them.
//
//  Sequence numbers are persisted to a small store file so a restart resumes
//  the session instead of forcing a mass resend.
// ============================================================================
#pragma once

#include <cstdint>
#include <functional>
#include <string>

#include "fix/FixMessage.hpp"

namespace rts::fix {

enum class SessionState : std::uint8_t {
    Disconnected, LogonSent, LoggedOn, LogoutSent
};

struct SessionConfig {
    std::string senderCompId;
    std::string targetCompId;
    int         heartbeatSecs{30};
    std::string beginString{"FIX.4.4"};
    std::string storePath;          // empty -> sequence numbers not persisted
};

using AppMessageHandler = std::function<void(const FixMessageView&)>;
using WireSink          = std::function<void(const char*, std::size_t)>;
using StateHandler      = std::function<void(SessionState)>;

// UTC timestamp in FIX format: YYYYMMDD-HH:MM:SS.sss
[[nodiscard]] std::string nowFixTimestamp();

class FixSession {
public:
    FixSession(SessionConfig cfg, WireSink sink, AppMessageHandler onApp);

    void setStateHandler(StateHandler h) { onState_ = std::move(h); }

    void connect();      // sends Logon
    void disconnect();   // sends Logout

    void onBytes(const char* data, std::size_t len);  // drives parser + FSM
    void onTimer(std::int64_t nowMs);                 // heartbeats / staleness

    // Claim the next outbound sequence number (for app messages the caller
    // encodes itself via FixCodec) and forward fully-framed bytes to the wire.
    [[nodiscard]] std::uint64_t claimSeq() noexcept;
    void sendBytes(std::string_view framed);

    [[nodiscard]] SessionState  state()         const noexcept { return state_; }
    [[nodiscard]] std::uint64_t nextOutSeq()     const noexcept { return outSeq_; }
    [[nodiscard]] std::uint64_t expectedInSeq()  const noexcept { return inSeq_; }
    [[nodiscard]] bool          loggedOn()       const noexcept {
        return state_ == SessionState::LoggedOn;
    }

private:
    void setState(SessionState s);
    void handleAdmin(const FixMessageView& msg);
    void sendLogon();
    void sendLogout();
    void sendHeartbeat(std::string_view testReqId = {});
    void sendTestRequest();
    void sendResendRequest(std::uint64_t from, std::uint64_t to);
    void loadSeqNums();
    void persistSeqNums();

    SessionConfig     cfg_;
    WireSink          sink_;
    AppMessageHandler onApp_;
    StateHandler      onState_;

    SessionState  state_{SessionState::Disconnected};
    std::uint64_t outSeq_{1};
    std::uint64_t inSeq_{1};
    std::int64_t  lastRxMs_{0};
    std::int64_t  lastTxMs_{0};
    bool          awaitingPong_{false};

    std::string   rxBuffer_;
};

}  // namespace rts::fix
