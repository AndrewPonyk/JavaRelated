#pragma once

#include "engine/core/Result.hpp"
#include "engine/core/Types.hpp"
#include "engine/rendering/rhi/RHIDevice.hpp"

#include <memory>
#include <vector>

/// @file VulkanDevice.hpp
/// @brief Vulkan implementation of rhi::RHIDevice (offscreen rendering).
///
/// Renders into an offscreen color image (no surface/swapchain required), so it
/// produces real pixels headlessly — readable via readColorPixels(). The on-screen
/// swapchain path needs a window/SDL surface and is layered on later.
///
/// NOTE: This translation unit only compiles when ENGINE_HAS_VULKAN is defined by
/// CMake (i.e. the Vulkan SDK was found). It is written against the standard Vulkan
/// 1.0 API but has NOT been compiled/run in the authoring environment (no SDK there).

namespace engine::platform {
class Window;
}

namespace engine::rhi::vulkan {

class VulkanCommandList; // defined alongside VulkanDevice (needs its internal tables)

struct OffscreenConfig {
    u32 width  = 256;
    u32 height = 256;
};

class VulkanDevice final : public RHIDevice {
public:
    VulkanDevice();
    ~VulkanDevice() override;

    /// Bring up instance -> physical/logical device -> offscreen render target +
    /// render pass + framebuffer + command pool. `window` may be null (offscreen).
    [[nodiscard]] Result<bool> initialize(platform::Window* window, bool enableValidation,
                                          OffscreenConfig offscreen = {});

    [[nodiscard]] BackendType backend() const noexcept override { return BackendType::Vulkan; }

    [[nodiscard]] Result<BufferHandle>   createBuffer(const BufferDesc& desc) override;
    [[nodiscard]] Result<TextureHandle>  createTexture(const TextureDesc& desc) override;
    [[nodiscard]] Result<ShaderHandle>   createShader(const ShaderDesc& desc) override;
    [[nodiscard]] Result<PipelineHandle> createGraphicsPipeline(ShaderHandle vs,
                                                                ShaderHandle fs) override;

    void destroyBuffer(BufferHandle handle) override;
    void destroyTexture(TextureHandle handle) override;

    [[nodiscard]] CommandList* beginFrame() override;
    void                       endFrame() override;
    void                       present() override;
    void                       waitIdle() override;
    void                       onResize(u32 width, u32 height) override;

    /// Read the offscreen color target back to CPU as tightly-packed RGBA8 bytes.
    [[nodiscard]] Result<std::vector<u8>> readColorPixels(u32& outWidth, u32& outHeight);

    [[nodiscard]] u32 renderWidth() const noexcept;
    [[nodiscard]] u32 renderHeight() const noexcept;

private:
    friend class VulkanCommandList; // resolves RHI handles -> Vk objects via impl_

    struct Impl;
    std::unique_ptr<Impl> impl_;
    bool                  validation_ = false;
};

} // namespace engine::rhi::vulkan
