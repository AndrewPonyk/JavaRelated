/// @file main.cpp
/// @brief Offscreen Vulkan smoke test — renders the triangle into an image, reads it
/// back, writes triangle.ppm, and verifies non-background pixels exist.
///
/// This is the headless "did the render graph produce pixels?" check. Build it with
/// the Vulkan SDK present (provides headers + glslc); run the resulting
/// `vulkan_offscreen_triangle` binary and inspect triangle.ppm / the exit code.
///
/// Exit codes: 0 = a triangle was rendered; non-zero = a stage failed.

#include "engine/core/Log.hpp"
#include "engine/core/Types.hpp"
#include "engine/platform/Filesystem.hpp"
#include "engine/rendering/RenderGraph.hpp"
#include "engine/rendering/rhi/RHIDevice.hpp"
#include "engine/rendering/vulkan/VulkanDevice.hpp"

#include <string>
#include <vector>

#ifndef ENGINE_VULKAN_SHADER_DIR
    #define ENGINE_VULKAN_SHADER_DIR "."
#endif

using namespace engine;

int main() {
    log::init(log::Level::Debug);

    rhi::vulkan::VulkanDevice device;
    if (auto r = device.initialize(nullptr, /*validation*/ true, {256, 256}); !r) {
        log::error("[offscreen] device init failed: {}", r.error().message);
        return 1;
    }

    // Load the SPIR-V that CMake compiled from assets/shaders/*.
    const std::string dir = ENGINE_VULKAN_SHADER_DIR;
    auto              vsBytes = platform::fs::readBytes(dir + "/triangle.vert.spv");
    auto              fsBytes = platform::fs::readBytes(dir + "/triangle.frag.spv");
    if (!vsBytes || !fsBytes) {
        log::error("[offscreen] could not load compiled shaders from {}", dir);
        return 2;
    }

    rhi::ShaderDesc vd;
    vd.stage     = rhi::ShaderStage::Vertex;
    vd.spirv     = vsBytes.value().data();
    vd.spirvSize = vsBytes.value().size();
    rhi::ShaderDesc fd;
    fd.stage     = rhi::ShaderStage::Fragment;
    fd.spirv     = fsBytes.value().data();
    fd.spirvSize = fsBytes.value().size();

    auto vs = device.createShader(vd);
    auto fs = device.createShader(fd);
    if (!vs || !fs) {
        log::error("[offscreen] createShader failed");
        return 3;
    }
    auto pipeline = device.createGraphicsPipeline(vs.value(), fs.value());
    if (!pipeline) {
        log::error("[offscreen] pipeline failed: {}", pipeline.error().message);
        return 4;
    }
    const auto pipeHandle = pipeline.value();

    // Record one frame through the render graph.
    rhi::CommandList* cmd = device.beginFrame();
    if (cmd == nullptr) {
        log::error("[offscreen] beginFrame failed");
        return 5;
    }
    rendering::RenderGraph graph;
    graph.addPass(
        "triangle",
        [](rendering::RenderGraphBuilder& b) {
            const rendering::ResourceId backbuffer = b.importBackbuffer();
            b.write(backbuffer);
        },
        [&](rhi::CommandList& c) {
            c.beginRenderPass(rhi::RenderPassHandle{}, rhi::ClearColor{0.10f, 0.10f, 0.15f, 1.0f});
            c.bindPipeline(pipeHandle);
            c.draw(3);
            c.endRenderPass();
        });
    graph.compile();
    graph.execute(*cmd);
    device.endFrame();

    // Read back and write a PPM (P6).
    u32  width = 0;
    u32  height = 0;
    auto pixels = device.readColorPixels(width, height);
    if (!pixels) {
        log::error("[offscreen] readback failed: {}", pixels.error().message);
        return 6;
    }
    const std::vector<u8>& px = pixels.value();

    std::string     header = "P6\n" + std::to_string(width) + " " + std::to_string(height) + "\n255\n";
    std::vector<u8> ppm(header.begin(), header.end());
    ppm.reserve(ppm.size() + static_cast<usize>(width) * height * 3);
    u64 nonBackground = 0;
    for (u32 i = 0; i < width * height; ++i) {
        const u8 r = px[i * 4 + 0];
        const u8 g = px[i * 4 + 1];
        const u8 b = px[i * 4 + 2];
        ppm.push_back(r);
        ppm.push_back(g);
        ppm.push_back(b);
        if (r > 200 || g > 200 || b > 200) { // clear color is ~(26,26,38)
            ++nonBackground;
        }
    }
    (void) platform::fs::writeBytesAtomic("triangle.ppm", ppm);

    device.waitIdle();
    log::info("[offscreen] {}x{}, non-background pixels = {} -> wrote triangle.ppm", width, height,
              nonBackground);
    log::shutdown();
    return nonBackground > 0 ? 0 : 7; // fail if the triangle did not render
}
