#pragma once

#include "engine/core/Log.hpp"

#include <cstdlib>

/// @file Assert.hpp
/// @brief Invariant checks for programmer errors. Active in debug/checked builds,
///        compiled out in shipping builds. These catch *bugs*, not runtime
///        conditions — use Result<T,E> for recoverable failures.

#if defined(_MSC_VER)
    #define ENGINE_DEBUG_BREAK() __debugbreak()
#elif defined(__GNUC__) || defined(__clang__)
    #define ENGINE_DEBUG_BREAK() __builtin_trap()
#else
    #define ENGINE_DEBUG_BREAK() std::abort()
#endif

#if defined(ENGINE_ENABLE_ASSERTS)

    #define ENGINE_ASSERT(cond, ...)                                              \
        do {                                                                      \
            if (!(cond)) {                                                        \
                ::engine::log::critical("Assertion failed: {} | {}", #cond,       \
                                        ::engine::log::format(__VA_ARGS__));      \
                ENGINE_DEBUG_BREAK();                                             \
            }                                                                     \
        } while (false)

    /// Unconditional failure (e.g., unreachable switch default in debug).
    #define ENGINE_UNREACHABLE(...)                                               \
        do {                                                                      \
            ::engine::log::critical("Unreachable code reached: {}",               \
                                    ::engine::log::format(__VA_ARGS__));          \
            ENGINE_DEBUG_BREAK();                                                 \
        } while (false)

#else
    #define ENGINE_ASSERT(cond, ...) ((void) 0)
    #define ENGINE_UNREACHABLE(...)  ENGINE_DEBUG_BREAK()
#endif

/// Always-checked variant (kept even in shipping for critical invariants).
#define ENGINE_VERIFY(cond, ...)                                                  \
    do {                                                                          \
        if (!(cond)) {                                                            \
            ::engine::log::critical("Verify failed: {} | {}", #cond,              \
                                    ::engine::log::format(__VA_ARGS__));          \
            ENGINE_DEBUG_BREAK();                                                 \
        }                                                                         \
    } while (false)
