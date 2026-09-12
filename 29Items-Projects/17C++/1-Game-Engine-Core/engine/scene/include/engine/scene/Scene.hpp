#pragma once

#include "engine/core/Result.hpp"
#include "engine/core/Types.hpp"
#include "engine/core/ecs/Registry.hpp"
#include "engine/scene/Components.hpp"

#include <string>

/// @file Scene.hpp
/// @brief A world: owns an ECS Registry plus (de)serialization and spawn helpers.
///
/// The scene is the unit of save/load. Serialization round-trips the Registry to/from
/// JSON (CBOR for shipped data). Systems are run by the runtime against scene.registry().

namespace engine::scene {

class Scene {
public:
    explicit Scene(std::string name = "Untitled") : name_(std::move(name)) {}

    [[nodiscard]] ecs::Registry&       registry() noexcept { return registry_; }
    [[nodiscard]] const ecs::Registry& registry() const noexcept { return registry_; }
    [[nodiscard]] const std::string&   name() const noexcept { return name_; }

    /// Spawn an entity with a Transform (+ optional Name).
    ecs::Entity spawn(const std::string& name = {});

    /// Spawn a renderable (Transform + MeshRenderer).
    ecs::Entity spawnMesh(resources::AssetHandle mesh, resources::AssetHandle material,
                          const Transform& transform = {});

    // --- Serialization (the save/load boundary) ---
    // Non-const: enumerating component pools lazily instantiates them in the Registry.
    [[nodiscard]] Result<std::string> serialize();                // -> JSON
    [[nodiscard]] Result<bool>        deserialize(std::string_view json); // <- JSON
    [[nodiscard]] Result<bool>        saveToFile(const std::string& path);
    [[nodiscard]] Result<bool>        loadFromFile(const std::string& path);

    void clear();

private:
    std::string   name_;
    ecs::Registry registry_;
};

} // namespace engine::scene
