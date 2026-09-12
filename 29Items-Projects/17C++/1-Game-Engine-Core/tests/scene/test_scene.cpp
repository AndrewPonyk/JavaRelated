#include "engine/scene/Scene.hpp"

#include <gtest/gtest.h>

#include <cmath>
#include <cstdio>
#include <string>

using namespace engine;
using namespace engine::scene;

TEST(Scene, SpawnAddsTransformAndName) {
    Scene      s("Test");
    const auto e = s.spawn("Hero");
    EXPECT_TRUE(s.registry().has<Transform>(e));
    EXPECT_TRUE(s.registry().has<Name>(e));
    EXPECT_EQ(s.registry().aliveCount(), 1u);
}

TEST(Scene, SerializeDeserializeRoundTrip) {
    Scene      s("World");
    const auto a = s.spawn("Alpha");
    s.registry().get<Transform>(a).position = {1.0f, 2.0f, 3.0f};
    const auto b = s.spawn("Beta");
    s.registry().emplace<Camera>(b, Camera{70.0f, 0.5f, 500.0f, true});

    auto json = s.serialize();
    ASSERT_TRUE(json);

    Scene loaded("Empty");
    ASSERT_TRUE(loaded.deserialize(json.value()));
    EXPECT_EQ(loaded.name(), "World");
    EXPECT_EQ(loaded.registry().aliveCount(), 2u);

    int   cameras = 0;
    float fov     = 0.0f;
    loaded.registry().view<Camera>().each([&](ecs::Entity, Camera& c) {
        ++cameras;
        fov = c.fovDegrees;
    });
    EXPECT_EQ(cameras, 1);
    EXPECT_FLOAT_EQ(fov, 70.0f);

    bool transformPreserved = false;
    loaded.registry().view<Transform>().each([&](ecs::Entity, Transform& t) {
        if (std::fabs(t.position.x - 1.0f) < 1e-3f && std::fabs(t.position.y - 2.0f) < 1e-3f) {
            transformPreserved = true;
        }
    });
    EXPECT_TRUE(transformPreserved);
}

TEST(Scene, SaveToAndLoadFromFile) {
    Scene s("Persisted");
    s.spawn("X");
    s.spawn("Y");

    const std::string path = "test_scene_roundtrip.json";
    ASSERT_TRUE(s.saveToFile(path));

    Scene loaded("Loaded");
    ASSERT_TRUE(loaded.loadFromFile(path));
    EXPECT_EQ(loaded.registry().aliveCount(), 2u);

    std::remove(path.c_str());
}

#ifdef ENGINE_ASSETS_DIR
TEST(Scene, LoadsShippedExampleAsset) {
    // The example scene that ships in assets/ must load through the engine and match
    // its serialization format (guards against doc/code format drift).
    Scene             s("placeholder");
    const std::string path = std::string(ENGINE_ASSETS_DIR) + "/scenes/example.scene.json";
    ASSERT_TRUE(s.loadFromFile(path));
    EXPECT_EQ(s.name(), "Example Scene");
    EXPECT_EQ(s.registry().aliveCount(), 3u);

    int activeCameras = 0;
    s.registry().view<Camera>().each([&](ecs::Entity, Camera& c) {
        if (c.active) {
            ++activeCameras;
        }
    });
    EXPECT_EQ(activeCameras, 1);
}
#endif

TEST(Scene, ClearRemovesEntities) {
    Scene s("C");
    s.spawn("a");
    s.spawn("b");
    EXPECT_EQ(s.registry().aliveCount(), 2u);
    s.clear();
    EXPECT_EQ(s.registry().aliveCount(), 0u);
}
