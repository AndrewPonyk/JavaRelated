#pragma once

#include "engine/core/Types.hpp"

#include <chrono>

/// @file Clock.hpp
/// @brief High-resolution frame timing + fixed-timestep accumulator.

namespace engine::platform {

class Clock {
public:
    Clock() : last_(Steady::now()) {}

    /// Call once per frame; returns elapsed seconds since the previous call.
    f32 tick() {
        const auto now    = Steady::now();
        const f32  delta  = std::chrono::duration<f32>(now - last_).count();
        last_             = now;
        elapsed_         += delta;
        ++frameCount_;
        return delta;
    }

    [[nodiscard]] f32 elapsedSeconds() const noexcept { return elapsed_; }
    [[nodiscard]] u64 frameCount() const noexcept { return frameCount_; }

private:
    using Steady = std::chrono::steady_clock;
    Steady::time_point last_;
    f32                elapsed_    = 0.0f;
    u64                frameCount_ = 0;
};

/// Drives fixed-timestep simulation independent of render frame rate.
class FixedTimestep {
public:
    explicit FixedTimestep(f32 step = 1.0f / 60.0f) : step_(step) {}

    /// Accumulate frame time; returns how many fixed steps to run this frame.
    [[nodiscard]] u32 consume(f32 frameDelta) {
        accumulator_ += frameDelta;
        u32 steps = 0;
        while (accumulator_ >= step_) {
            accumulator_ -= step_;
            ++steps;
        }
        return steps;
    }

    [[nodiscard]] f32 step() const noexcept { return step_; }
    /// Fraction into the next step, for render interpolation.
    [[nodiscard]] f32 alpha() const noexcept { return accumulator_ / step_; }

private:
    f32 step_        = 1.0f / 60.0f;
    f32 accumulator_ = 0.0f;
};

} // namespace engine::platform
