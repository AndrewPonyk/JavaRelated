#include "engine/core/Transform.hpp"
#include "engine/core/math/Mat4.hpp"
#include "engine/core/math/Vec.hpp"

#include <gtest/gtest.h>

using namespace engine::math;

TEST(Math, DotProduct) {
    EXPECT_FLOAT_EQ(dot(Vec3{1, 0, 0}, Vec3{0, 1, 0}), 0.0f);
    EXPECT_FLOAT_EQ(dot(Vec3{2, 3, 4}, Vec3{2, 3, 4}), 29.0f);
}

TEST(Math, CrossProduct) {
    const Vec3 c = cross(Vec3{1, 0, 0}, Vec3{0, 1, 0});
    EXPECT_FLOAT_EQ(c.x, 0.0f);
    EXPECT_FLOAT_EQ(c.y, 0.0f);
    EXPECT_FLOAT_EQ(c.z, 1.0f);
}

TEST(Math, Normalize) {
    const Vec3 n = normalize(Vec3{3, 0, 0});
    EXPECT_FLOAT_EQ(length(n), 1.0f);
    EXPECT_FLOAT_EQ(n.x, 1.0f);
}

TEST(Math, Mat4Identity) {
    const Mat4 m = Mat4::identity();
    EXPECT_FLOAT_EQ(m.m[0][0], 1.0f);
    EXPECT_FLOAT_EQ(m.m[1][1], 1.0f);
    EXPECT_FLOAT_EQ(m.m[1][0], 0.0f);
}

TEST(Math, Mat4TranslationTimesIdentity) {
    const Mat4 t = Mat4::translation(Vec3{1, 2, 3});
    const Mat4 r = t * Mat4::identity();
    EXPECT_FLOAT_EQ(r.m[3][0], 1.0f);
    EXPECT_FLOAT_EQ(r.m[3][1], 2.0f);
    EXPECT_FLOAT_EQ(r.m[3][2], 3.0f);
}

TEST(Math, RotationZByNinetyMapsXTowardY) {
    const Mat4 rz = Mat4::rotationEulerDegrees(Vec3{0, 0, 90});
    EXPECT_NEAR(rz.m[0][0], 0.0f, 1e-5); // cos(90) ~ 0
    EXPECT_NEAR(rz.m[0][1], 1.0f, 1e-5); // sin(90) ~ 1  (+X column rotates toward +Y)
}

TEST(Math, TransformMatrixCombinesScaleAndTranslation) {
    engine::Transform t;
    t.position = {5, 6, 7};
    t.scale    = {2, 2, 2};
    const Mat4 m = t.localMatrix();
    EXPECT_FLOAT_EQ(m.m[3][0], 5.0f); // translation column
    EXPECT_FLOAT_EQ(m.m[3][1], 6.0f);
    EXPECT_FLOAT_EQ(m.m[0][0], 2.0f); // scale on the diagonal (identity rotation)
}
