#include "engine/core/ecs/Registry.hpp"

/// @file Registry.cpp
/// @brief Out-of-line definitions for the (mostly header-only, templated) Registry.

namespace engine::ecs::detail {

u32 nextComponentTypeId() {
    // A single global counter shared across all translation units. Each component
    // type latches its id once via the static in componentTypeId<T>().
    static u32 counter = 0;
    return counter++;
}

} // namespace engine::ecs::detail
