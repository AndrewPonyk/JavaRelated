#pragma once

#include "engine/core/Types.hpp"

/// @file Entity.hpp
/// @brief Entity handle = index + generation, packed into 32 bits.
///
/// The generation counter is the defense against the classic "stale handle" bug:
/// when an entity slot is recycled its generation is bumped, so a handle held from
/// before the recycle compares invalid. See docs/adr/0001-ecs-sparse-set.md.

namespace engine::ecs {

/// Underlying storage of an entity handle.
using EntityId = u32;

/// 20 bits of index (~1M live entities) + 12 bits of generation.
inline constexpr u32 kEntityIndexBits      = 20;
inline constexpr u32 kEntityGenerationBits = 12;
inline constexpr EntityId kEntityIndexMask = (EntityId{1} << kEntityIndexBits) - 1;
inline constexpr EntityId kEntityGenMask   = (EntityId{1} << kEntityGenerationBits) - 1;

/// Sentinel for "no entity".
inline constexpr EntityId kNullEntity = kEntityIndexMask; // index == max, gen 0

struct Entity {
    EntityId id = kNullEntity;

    constexpr Entity() = default;
    constexpr explicit Entity(EntityId raw) : id(raw) {}

    [[nodiscard]] constexpr u32 index() const noexcept { return id & kEntityIndexMask; }
    [[nodiscard]] constexpr u32 generation() const noexcept {
        return (id >> kEntityIndexBits) & kEntityGenMask;
    }
    [[nodiscard]] constexpr bool valid() const noexcept { return index() != kEntityIndexMask; }

    friend constexpr bool operator==(Entity a, Entity b) noexcept { return a.id == b.id; }
    friend constexpr bool operator!=(Entity a, Entity b) noexcept { return a.id != b.id; }
};

/// Compose a handle from an index + generation.
[[nodiscard]] constexpr Entity makeEntity(u32 index, u32 generation) noexcept {
    return Entity{(index & kEntityIndexMask)
                  | ((generation & kEntityGenMask) << kEntityIndexBits)};
}

inline constexpr Entity kNull{kNullEntity};

} // namespace engine::ecs
