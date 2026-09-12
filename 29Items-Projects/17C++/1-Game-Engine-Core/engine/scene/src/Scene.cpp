#include "engine/scene/Scene.hpp"

#include "engine/core/Json.hpp"
#include "engine/core/Log.hpp"
#include "engine/platform/Filesystem.hpp"
#include "engine/resources/Serialization.hpp"

#include <vector>

/// @file Scene.cpp
/// @brief Scene spawn helpers + real JSON (de)serialization of the ECS world.
///
/// Serialization faithfully round-trips the runtime components defined in
/// Components.hpp. Each entity becomes a JSON object with a `components` map keyed by
/// component name, mirroring assets/scenes/example.scene.json.

namespace engine::scene {
namespace {
constexpr int kSceneVersion = 1;

Json transformToJson(const Transform& t) {
    Json j          = Json::object();
    j["position"]     = resources::toJson(t.position);
    j["eulerDegrees"] = resources::toJson(t.eulerDegrees);
    j["scale"]        = resources::toJson(t.scale);
    return j;
}

Transform transformFromJson(const Json& j) {
    Transform t;
    t.position     = resources::vec3FromJson(j.at("position"), t.position);
    t.eulerDegrees = resources::vec3FromJson(j.at("eulerDegrees"), t.eulerDegrees);
    t.scale        = resources::vec3FromJson(j.at("scale"), t.scale);
    return t;
}

Json assetToJson(const resources::AssetHandle& h) {
    Json j   = Json::object();
    j["id"]   = h.id;
    j["type"] = static_cast<u32>(h.type);
    return j;
}

resources::AssetHandle assetFromJson(const Json& j) {
    return resources::AssetHandle{static_cast<u32>(j.at("id").asInt(0xFFFFFFFF)),
                                  static_cast<resources::AssetType>(j.at("type").asInt())};
}
} // namespace

ecs::Entity Scene::spawn(const std::string& name) {
    const ecs::Entity e = registry_.create();
    registry_.emplace<Transform>(e);
    if (!name.empty()) {
        registry_.emplace<Name>(e, Name{name});
    }
    return e;
}

ecs::Entity Scene::spawnMesh(resources::AssetHandle mesh, resources::AssetHandle material,
                             const Transform& transform) {
    const ecs::Entity e = registry_.create();
    registry_.emplace<Transform>(e, transform);
    registry_.emplace<MeshRenderer>(e, MeshRenderer{mesh, material, true});
    return e;
}

Result<std::string> Scene::serialize() {
    Json root        = Json::object();
    root["name"]     = name_;
    root["version"]  = kSceneVersion;
    Json entities    = Json::array();

    // Scene objects are those with a Transform (the spatial root of the data model).
    registry_.view<Transform>().each([&](ecs::Entity e, Transform& tf) {
        Json comps           = Json::object();
        comps["Transform"]   = transformToJson(tf);

        if (auto* n = registry_.tryGet<Name>(e)) {
            Json jn    = Json::object();
            jn["value"] = n->value;
            comps["Name"] = std::move(jn);
        }
        if (auto* mr = registry_.tryGet<MeshRenderer>(e)) {
            Json jm        = Json::object();
            jm["mesh"]     = assetToJson(mr->mesh);
            jm["material"] = assetToJson(mr->material);
            jm["visible"]  = mr->visible;
            comps["MeshRenderer"] = std::move(jm);
        }
        if (auto* cam = registry_.tryGet<Camera>(e)) {
            Json jc          = Json::object();
            jc["fovDegrees"] = cam->fovDegrees;
            jc["nearPlane"]  = cam->nearPlane;
            jc["farPlane"]   = cam->farPlane;
            jc["active"]     = cam->active;
            comps["Camera"]  = std::move(jc);
        }
        if (auto* tag = registry_.tryGet<TerrainTileTag>(e)) {
            Json jt    = Json::object();
            jt["tileX"] = tag->tileX;
            jt["tileY"] = tag->tileY;
            comps["TerrainTileTag"] = std::move(jt);
        }

        Json je          = Json::object();
        je["components"] = std::move(comps);
        entities.push_back(std::move(je));
    });

    root["entities"] = std::move(entities);
    return ok(root.dump(2));
}

Result<bool> Scene::deserialize(std::string_view json) {
    auto parsed = Json::parse(json);
    if (!parsed) {
        return parsed.error();
    }
    const Json& root = parsed.value();
    registry_.clear();
    name_ = root.at("name").asString(name_);

    for (const Json& je : root.at("entities").arr()) {
        const Json&       comps = je.at("components");
        const ecs::Entity e     = registry_.create();

        if (comps.contains("Transform")) {
            registry_.emplace<Transform>(e, transformFromJson(comps.at("Transform")));
        } else {
            registry_.emplace<Transform>(e); // every scene entity has a transform
        }
        if (comps.contains("Name")) {
            registry_.emplace<Name>(e, Name{comps.at("Name").at("value").asString()});
        }
        if (comps.contains("MeshRenderer")) {
            const Json& jm = comps.at("MeshRenderer");
            registry_.emplace<MeshRenderer>(
                e, MeshRenderer{assetFromJson(jm.at("mesh")), assetFromJson(jm.at("material")),
                                jm.at("visible").asBool(true)});
        }
        if (comps.contains("Camera")) {
            const Json& jc = comps.at("Camera");
            registry_.emplace<Camera>(e, Camera{jc.at("fovDegrees").asFloat(60.0f),
                                                jc.at("nearPlane").asFloat(0.1f),
                                                jc.at("farPlane").asFloat(1000.0f),
                                                jc.at("active").asBool(false)});
        }
        if (comps.contains("TerrainTileTag")) {
            const Json& jt = comps.at("TerrainTileTag");
            registry_.emplace<TerrainTileTag>(
                e, TerrainTileTag{static_cast<i32>(jt.at("tileX").asInt()),
                                  static_cast<i32>(jt.at("tileY").asInt())});
        }
    }
    log::info("[Scene] deserialized '{}' ({} entities)", name_, registry_.aliveCount());
    return ok(true);
}

Result<bool> Scene::saveToFile(const std::string& path) {
    auto text = serialize();
    if (!text) {
        return text.error();
    }
    const std::vector<u8> bytes(text.value().begin(), text.value().end());
    return platform::fs::writeBytesAtomic(path, bytes);
}

Result<bool> Scene::loadFromFile(const std::string& path) {
    auto text = platform::fs::readText(path);
    if (!text) {
        return text.error();
    }
    return deserialize(text.value());
}

void Scene::clear() {
    registry_.clear();
}

} // namespace engine::scene
