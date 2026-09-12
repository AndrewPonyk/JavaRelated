#include "engine/resources/Serialization.hpp"

#include <gtest/gtest.h>

using namespace engine;
using namespace engine::resources;

TEST(Serialization, ContentHashIsStableAndDistinct) {
    EXPECT_EQ(contentHash("hello"), contentHash("hello"));
    EXPECT_NE(contentHash("hello"), contentHash("world"));
    EXPECT_EQ(contentHash("hello").size(), 16u); // 64-bit hex
}

TEST(Serialization, Vec3JsonRoundTrip) {
    const math::Vec3 v{1.5f, -2.0f, 3.25f};
    const Json       j    = toJson(v);
    const math::Vec3 back = vec3FromJson(j);
    EXPECT_FLOAT_EQ(back.x, 1.5f);
    EXPECT_FLOAT_EQ(back.y, -2.0f);
    EXPECT_FLOAT_EQ(back.z, 3.25f);
}

TEST(Serialization, Vec3FromJsonUsesFallbackOnBadInput) {
    const Json       notAnArray = Json::object();
    const math::Vec3 fallback{9, 9, 9};
    const math::Vec3 r = vec3FromJson(notAnArray, fallback);
    EXPECT_FLOAT_EQ(r.x, 9.0f);
}
