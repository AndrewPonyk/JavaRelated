#pragma once

#include "engine/core/Types.hpp"

#include <string>
#include <string_view>

#if defined(__cpp_lib_format)
    #include <format>
#endif

/// @file Log.hpp
/// @brief Lightweight structured-logging facade.
///
/// In production this wraps spdlog (async sink, rotating file, in-editor console).
/// The facade keeps subsystems free of any concrete logger dependency and lets
/// TRACE/DEBUG be stripped from shipping builds at compile time.

namespace engine::log {

enum class Level : u8 {
    Trace,
    Debug,
    Info,
    Warn,
    Error,
    Critical,
};

/// Initialize sinks/level. Called once from the composition root.
void init(Level minLevel = Level::Info);
void shutdown();
void setLevel(Level level);

/// Type-erased log entry point implemented in Log.cpp.
void logMessage(Level level, std::string_view channel, std::string_view message);

/// Compile-time-safe formatting helper. Uses std::format when available,
/// otherwise returns the raw format string (fine for stub builds).
inline std::string format() { return {}; }

template <typename... Args>
std::string format([[maybe_unused]] std::string_view fmt, [[maybe_unused]] Args&&... args) {
#if defined(__cpp_lib_format)
    if constexpr (sizeof...(Args) == 0) {
        return std::string{fmt};
    } else {
        return std::vformat(fmt, std::make_format_args(args...));
    }
#else
    return std::string{fmt};
#endif
}

template <typename... Args>
void trace(std::string_view fmt, Args&&... args) {
#if !defined(ENGINE_SHIPPING)
    logMessage(Level::Trace, "Engine", format(fmt, std::forward<Args>(args)...));
#endif
}

template <typename... Args>
void debug(std::string_view fmt, Args&&... args) {
#if !defined(ENGINE_SHIPPING)
    logMessage(Level::Debug, "Engine", format(fmt, std::forward<Args>(args)...));
#endif
}

template <typename... Args>
void info(std::string_view fmt, Args&&... args) {
    logMessage(Level::Info, "Engine", format(fmt, std::forward<Args>(args)...));
}

template <typename... Args>
void warn(std::string_view fmt, Args&&... args) {
    logMessage(Level::Warn, "Engine", format(fmt, std::forward<Args>(args)...));
}

template <typename... Args>
void error(std::string_view fmt, Args&&... args) {
    logMessage(Level::Error, "Engine", format(fmt, std::forward<Args>(args)...));
}

template <typename... Args>
void critical(std::string_view fmt, Args&&... args) {
    logMessage(Level::Critical, "Engine", format(fmt, std::forward<Args>(args)...));
}

} // namespace engine::log
