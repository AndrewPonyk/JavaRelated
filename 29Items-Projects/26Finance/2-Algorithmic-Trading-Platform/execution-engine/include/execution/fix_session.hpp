// FIX 4.4 session abstraction.
//
// In production this wraps QuickFIX/C++ (a battle-tested engine that handles
// sequence numbers, resend/gap-fill, and logon — TECH-NOTES §3.6). The interface
// here decouples the order router from the concrete FIX library for testability.
#pragma once

#include <functional>

#include "execution/types.hpp"

namespace exec {

// Callback invoked when an ExecutionReport (35=8) arrives from the broker.
using ExecReportHandler = std::function<void(const ExecReport&)>;

class IFixSession {
public:
    virtual ~IFixSession() = default;

    // Establish the FIX session (Logon, 35=A). Returns false on failure.
    virtual bool logon() = 0;

    // Send a NewOrderSingle (35=D). Returns false if the session is down.
    virtual bool send_new_order(const Order& order) = 0;

    // Send an OrderCancelRequest (35=F).
    virtual bool send_cancel(std::uint64_t order_id) = 0;

    // Register the handler for inbound execution reports.
    virtual void on_exec_report(ExecReportHandler handler) = 0;

    virtual bool is_connected() const = 0;
};

// Concrete QuickFIX-backed implementation (stub — see fix_session.cpp).
class QuickFixSession final : public IFixSession {
public:
    QuickFixSession(const char* sender_comp_id, const char* target_comp_id);
    ~QuickFixSession() override;

    bool logon() override;
    bool send_new_order(const Order& order) override;
    bool send_cancel(std::uint64_t order_id) override;
    void on_exec_report(ExecReportHandler handler) override;
    bool is_connected() const override;

private:
    const char* sender_comp_id_;
    const char* target_comp_id_;
    bool connected_{false};
    ExecReportHandler handler_{};
};

}  // namespace exec
