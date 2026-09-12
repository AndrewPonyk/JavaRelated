#pragma once

#include "engine/core/Transform.hpp"
#include "engine/core/Types.hpp"
#include "engine/core/ecs/Entity.hpp"
#include "engine/core/math/Vec.hpp"
#include "engine/resources/AssetManager.hpp"

#include <string>

/// @file Components.hpp
/// @brief Common, plain-data scene components. Transform is re-exported from core.
///
/// Components are small, trivially-copyable structs (data-oriented). Behaviour lives
/// in systems, not here. Serialization opts in via a `serialize(Archive&)` member
/// (see resources/Serialization.hpp).

namespace engine::scene {

using engine::Transform; // re-export the core spatial component

/// Human-readable name / editor label.
struct Name {
    std::string value;
};

/// Parent link for hierarchical transforms (root == null entity).
struct Hierarchy {
    ecs::Entity parent{};
    u32         childCount = 0;
};

/// Renderable: references a mesh + material asset (resolved by the renderer).
struct MeshRenderer {
    resources::AssetHandle mesh{};
    resources::AssetHandle material{};
    bool                   visible = true;
};

/// Perspective camera. One entity is typically tagged as the active camera.
struct Camera {
    f32  fovDegrees = 60.0f;
    f32  nearPlane  = 0.1f;
    f32  farPlane   = 1000.0f;
    bool active     = false;
};

/// Tag marking an entity as a procedurally-generated terrain tile.
struct TerrainTileTag {
    i32 tileX = 0;
    i32 tileY = 0;
};

} // namespace engine::scene
