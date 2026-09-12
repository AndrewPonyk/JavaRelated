#include "execution/fix_session.hpp"

// NOTE: This is a stub. Production wires QuickFIX/C++:
//   - quickfix::Application + quickfix::MessageCracker
//   - persisted sequence numbers, resend/gap-fill handling
//   - SSL, heartbeats, logon credentials from Vault-injected env
// See TECH-NOTES §3.6 (FIX session quirks).

namespace exec {

QuickFixSession::QuickFixSession(const char* sender_comp_id, const char* target_comp_id)
    : sender_comp_id_(sender_comp_id), target_comp_id_(target_comp_id) {}

QuickFixSession::~QuickFixSession() = default;

bool QuickFixSession::logon() {
    // TODO: initiate quickfix::SocketInitiator, block until Logon (35=A) ack.
    connected_ = true;
    return connected_;
}

bool QuickFixSession::send_new_order(const Order& order) {
    if (!connected_) return false;
    // TODO: build FIX 35=D (NewOrderSingle): 11=ClOrdID, 55=Symbol, 54=Side,
    //       38=OrderQty, 40=OrdType, 44=Price; quickfix::Session::sendToTarget(...).
    (void)order;
    return true;
}

bool QuickFixSession::send_cancel(std::uint64_t order_id) {
    if (!connected_) return false;
    // TODO: build FIX 35=F (OrderCancelRequest).
    (void)order_id;
    return true;
}

void QuickFixSession::on_exec_report(ExecReportHandler handler) {
    handler_ = std::move(handler);
}

bool QuickFixSession::is_connected() const { return connected_; }

}  // namespace exec
