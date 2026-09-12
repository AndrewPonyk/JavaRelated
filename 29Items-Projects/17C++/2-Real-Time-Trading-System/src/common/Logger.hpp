// ============================================================================
//  common/Logger.hpp
//  Asynchronous, lock-free logging façade.
//
//  Hot-path threads MUST NOT format strings or touch I/O. They enqueue a small
//  POD record into an SPSC ring; a dedicated background thread drains the ring,
//  formats, and writes to disk/stdout. This keeps the trade path allocation-
//  and-syscall-free while preserving full observability.
//
//  THREADING CONTRACT: the ring is single-producer. The global instance is for
//  single-threaded / single-producer use. For multi-threaded production, give
//  each trading thread its OWN Logger/queue and have the drain thread fan over a
//  registry of them (per-core logging — also better for cache locality). Do not
//  log from multiple threads through one instance.
// ============================================================================
#pragma once

#include <atomic>
#include <cstdint>
#include <cstdio>
#include <string_view>
#include <thread>

#include "common/Types.hpp"
#include "core/lockfree/SPSCQueue.hpp"
#include "core/time/Clock.hpp"

namespace rts::log {

enum class Level : std::uint8_t { Trace, Debug, Info, Warn, Error, Fatal };

// Compact, trivially-copyable record. The message is a string_view into a
// static/interned literal -> no heap, no copy on the producer side.
struct Record {
    Nanos            ts{};
    Level            level{Level::Info};
    std::string_view msg;        // must outlive draining (use literals)
    std::int64_t     a{0};       // optional numeric context fields
    std::int64_t     b{0};
};

class Logger {
public:
    static Logger& instance() {
        static Logger logger;
        return logger;
    }

    // Producer side: wait-free enqueue. Drops (and counts) if the ring is full
    // rather than ever blocking a trading thread.
    void log(Level lvl, std::string_view msg, std::int64_t a = 0,
             std::int64_t b = 0) noexcept {
        Record r{clock_.now(), lvl, msg, a, b};
        if (!queue_.tryPush(r)) {
            dropped_.fetch_add(1, std::memory_order_relaxed);
        }
    }

    void start() {
        running_.store(true, std::memory_order_release);
        worker_ = std::thread([this] { drainLoop(); });
    }
    void stop() {
        running_.store(false, std::memory_order_release);
        if (worker_.joinable()) worker_.join();
    }

    [[nodiscard]] std::uint64_t dropped() const noexcept {
        return dropped_.load(std::memory_order_relaxed);
    }

private:
    Logger() : queue_(1u << 16) {}
    ~Logger() { stop(); }

    void drainLoop() {
        while (running_.load(std::memory_order_acquire)) {
            if (auto rec = queue_.tryPop()) {
                writeOut(*rec);
            } else {
                std::this_thread::yield();  // background thread, not a trading core
            }
        }
        while (auto rec = queue_.tryPop()) writeOut(*rec);  // flush on shutdown
        std::fflush(stderr);
    }

    static const char* levelName(Level l) noexcept {
        switch (l) {
            case Level::Trace: return "TRACE";
            case Level::Debug: return "DEBUG";
            case Level::Info:  return "INFO ";
            case Level::Warn:  return "WARN ";
            case Level::Error: return "ERROR";
            case Level::Fatal: return "FATAL";
        }
        return "?????";
    }

    // Formats off the hot path on the dedicated logging thread. A production
    // build would target a buffered, rotating file sink; stderr is fine here.
    static void writeOut(const Record& r) {
        if (r.a != 0 || r.b != 0) {
            std::fprintf(stderr, "[%s] %.*s (%lld, %lld)\n", levelName(r.level),
                         static_cast<int>(r.msg.size()), r.msg.data(),
                         static_cast<long long>(r.a), static_cast<long long>(r.b));
        } else {
            std::fprintf(stderr, "[%s] %.*s\n", levelName(r.level),
                         static_cast<int>(r.msg.size()), r.msg.data());
        }
    }

    rts::time::SteadyClock          clock_;
    lockfree::SPSCQueue<Record>     queue_;
    std::atomic<bool>               running_{false};
    std::atomic<std::uint64_t>      dropped_{0};
    std::thread                     worker_;
};

// Ergonomic macros. String args should be literals / interned views.
#define RTS_LOG(lvl, msg, ...) \
    ::rts::log::Logger::instance().log((lvl), (msg), ##__VA_ARGS__)
#define RTS_INFO(msg, ...)  RTS_LOG(::rts::log::Level::Info,  (msg), ##__VA_ARGS__)
#define RTS_WARN(msg, ...)  RTS_LOG(::rts::log::Level::Warn,  (msg), ##__VA_ARGS__)
#define RTS_ERROR(msg, ...) RTS_LOG(::rts::log::Level::Error, (msg), ##__VA_ARGS__)

}  // namespace rts::log
