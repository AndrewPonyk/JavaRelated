#pragma once

#include "engine/core/Types.hpp"

#include <string_view>

/// @file System.hpp
/// @brief Base interface for ECS systems (the behaviour half of the ECS).
///
/// Systems are stateless w.r.t. game data — they operate over component arrays in
/// the Registry each tick. A system declares the components it reads/writes so the
/// scheduler can run non-conflicting systems in parallel on the job system.

namespace engine::ecs {

class Registry;

/// Fixed simulation step or variable frame delta, in seconds.
using DeltaTime = f32;

class ISystem {
public:
    virtual ~ISystem() = default;

    /// Human-readable name for profiling/scheduling diagnostics.
    [[nodiscard]] virtual std::string_view name() const noexcept = 0;

    /// One-time setup (subscribe to events, allocate caches).
    virtual void onStart(Registry& /*registry*/) {}

    /// Per-tick update over the registry.
    virtual void onUpdate(Registry& registry, DeltaTime dt) = 0;

    /// Teardown.
    virtual void onStop(Registry& /*registry*/) {}
};

} // namespace engine::ecs
