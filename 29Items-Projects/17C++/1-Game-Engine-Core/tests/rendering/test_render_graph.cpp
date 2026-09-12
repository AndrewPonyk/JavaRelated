#include "engine/platform/Window.hpp"
#include "engine/rendering/RenderGraph.hpp"
#include "engine/rendering/rhi/RHIDevice.hpp"

#include <gtest/gtest.h>

#include <string>
#include <vector>

using namespace engine;
using namespace engine::rendering;

namespace {
/// Minimal CommandList spy that counts draws (other calls are no-ops).
class SpyCommandList final : public rhi::CommandList {
public:
    int draws = 0;
    void beginRenderPass(rhi::RenderPassHandle, rhi::ClearColor) override {}
    void endRenderPass() override {}
    void bindPipeline(rhi::PipelineHandle) override {}
    void bindVertexBuffer(rhi::BufferHandle) override {}
    void setViewport(const rhi::Viewport&) override {}
    void draw(engine::u32, engine::u32) override { ++draws; }
};
} // namespace

TEST(RenderGraph, ExecutesIndependentPassesInOrder) {
    RenderGraph              graph;
    std::vector<std::string> order;
    graph.addPass(
        "shadow", [](RenderGraphBuilder&) {}, [&](rhi::CommandList&) { order.emplace_back("shadow"); });
    graph.addPass(
        "gbuffer", [](RenderGraphBuilder&) {},
        [&](rhi::CommandList& cmd) {
            order.emplace_back("gbuffer");
            cmd.draw(3);
        });
    graph.addPass(
        "present", [](RenderGraphBuilder&) {}, [&](rhi::CommandList&) { order.emplace_back("present"); });

    graph.compile();
    SpyCommandList cmd;
    graph.execute(cmd);

    ASSERT_EQ(order.size(), 3u);
    EXPECT_EQ(order[0], "shadow");
    EXPECT_EQ(order[1], "gbuffer");
    EXPECT_EQ(order[2], "present");
    EXPECT_EQ(cmd.draws, 1);
}

TEST(RenderGraph, OrdersProducerBeforeConsumerAndInsertsBarrier) {
    RenderGraph              graph;
    std::vector<std::string> order;
    ResourceId               gbuffer;

    graph.addPass(
        "geometry",
        [&](RenderGraphBuilder& b) {
            gbuffer = b.createTexture("gbuffer", {});
            b.write(gbuffer);
        },
        [&](rhi::CommandList&) { order.emplace_back("geometry"); });
    graph.addPass(
        "lighting",
        [&](RenderGraphBuilder& b) {
            b.read(gbuffer); // depends on "geometry"
            const ResourceId bb = b.importBackbuffer();
            b.write(bb);
        },
        [&](rhi::CommandList&) { order.emplace_back("lighting"); });

    graph.compile();
    EXPECT_EQ(graph.executionOrder().size(), 2u);
    EXPECT_GT(graph.barrierCount(), 0u); // read-after-write transition recorded

    SpyCommandList cmd;
    graph.execute(cmd);
    ASSERT_EQ(order.size(), 2u);
    EXPECT_EQ(order[0], "geometry");
    EXPECT_EQ(order[1], "lighting");
}

TEST(RenderGraph, CullsPassWithUnusedOutput) {
    RenderGraph graph;
    graph.addPass(
        "dead",
        [](RenderGraphBuilder& b) {
            const ResourceId t = b.createTexture("unused", {});
            b.write(t); // transient, never read -> dead
        },
        [](rhi::CommandList&) {});
    graph.addPass(
        "live",
        [](RenderGraphBuilder& b) {
            const ResourceId bb = b.importBackbuffer();
            b.write(bb); // imported -> always live
        },
        [](rhi::CommandList&) {});

    graph.compile();
    EXPECT_EQ(graph.executionOrder().size(), 1u);
    EXPECT_FALSE(graph.isLive(0));
    EXPECT_TRUE(graph.isLive(1));
}

TEST(RenderGraph, ResetClearsPasses) {
    RenderGraph graph;
    int         executed = 0;
    graph.addPass(
        "p", [](RenderGraphBuilder&) {}, [&](rhi::CommandList&) { ++executed; });
    graph.reset();

    SpyCommandList cmd;
    graph.execute(cmd);
    EXPECT_EQ(executed, 0);
    EXPECT_EQ(graph.passCount(), 0u);
}

TEST(NullDevice, FactoryProducesUsableDevice) {
    platform::Window dummy; // Null device ignores the window
    auto             dev = rhi::createDevice(rhi::BackendType::Null, dummy);
    ASSERT_TRUE(dev);
    EXPECT_EQ(dev.value()->backend(), rhi::BackendType::Null);

    rhi::BufferDesc desc;
    desc.size  = 256;
    auto buffer = dev.value()->createBuffer(desc);
    EXPECT_TRUE(buffer);

    // Zero-size buffer is rejected (validation).
    auto bad = dev.value()->createBuffer(rhi::BufferDesc{});
    EXPECT_FALSE(bad);
}
