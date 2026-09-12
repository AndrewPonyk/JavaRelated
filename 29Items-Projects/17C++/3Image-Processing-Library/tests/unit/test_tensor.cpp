// tests/unit/test_tensor.cpp
#include "imgproc/core/tensor.hpp"

#include <gtest/gtest.h>

namespace {

using imgproc::core::Device;
using imgproc::core::Tensor;

TEST(TensorTest, NumelMatchesShape) {
    Tensor t({1, 3, 4, 5}, Device::kCpu);
    EXPECT_EQ(t.numel(), 60u);
    EXPECT_EQ(t.device(), Device::kCpu);
}

TEST(TensorTest, FillAndRead) {
    Tensor t({2, 2}, Device::kCpu);
    t.fill(2.5F);
    const float* d = t.host_data();
    ASSERT_NE(d, nullptr);
    for (std::size_t i = 0; i < t.numel(); ++i) {
        EXPECT_FLOAT_EQ(d[i], 2.5F);
    }
}

TEST(TensorTest, HostCopyPreservesValues) {
    Tensor t({3}, Device::kCpu);
    t.host_data()[0] = 1.0F;
    t.host_data()[1] = 2.0F;
    t.host_data()[2] = 3.0F;
    Tensor copy;
    ASSERT_TRUE(t.to(Device::kCpu, copy).ok());
    ASSERT_EQ(copy.numel(), 3u);
    EXPECT_FLOAT_EQ(copy.host_data()[2], 3.0F);
}

TEST(TensorTest, MoveLeavesUsableTarget) {
    Tensor t({4}, Device::kCpu);
    t.fill(7.0F);
    Tensor moved = std::move(t);
    EXPECT_EQ(moved.numel(), 4u);
    EXPECT_FLOAT_EQ(moved.host_data()[0], 7.0F);
}

}  // namespace
