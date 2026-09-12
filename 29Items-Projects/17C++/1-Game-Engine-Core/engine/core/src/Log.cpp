#include "engine/core/Log.hpp"

#include <array>
#include <cstdio>
#include <mutex>

/// @file Log.cpp
/// @brief Stub logging sink. Production swaps this for spdlog (async + rotating
/// file + in-editor console) behind the same engine::log facade.

namespace engine::log {
namespace {

std::mutex g_mutex;
Level      g_minLevel = Level::Info;

constexpr std::string_view levelName(Level level) {
    switch (level) {
        case Level::Trace:    return "TRACE";
        case Level::Debug:    return "DEBUG";
        case Level::Info:     return "INFO";
        case Level::Warn:     return "WARN";
        case Level::Error:    return "ERROR";
        case Level::Critical: return "CRIT";
    }
    return "?????";
}

} // namespace

void init(Level minLevel) {
    setLevel(minLevel);
    info("[Log] initialized (min level = {})", levelName(minLevel));
}

void shutdown() {
    info("[Log] shutdown");
}

void setLevel(Level level) {
    std::scoped_lock lock(g_mutex);
    g_minLevel = level;
}

void logMessage(Level level, std::string_view channel, std::string_view message) {
    std::scoped_lock lock(g_mutex);
    if (static_cast<u8>(level) < static_cast<u8>(g_minLevel)) {
        return;
    }
    std::FILE* out = (level >= Level::Error) ? stderr : stdout;
    std::fprintf(out, "[%-5.*s][%.*s] %.*s\n",
                 static_cast<int>(levelName(level).size()), levelName(level).data(),
                 static_cast<int>(channel.size()), channel.data(),
                 static_cast<int>(message.size()), message.data());
}

} // namespace engine::log
