#pragma once

#include "engine/rendering/rhi/RHIDevice.hpp"

/// @file VulkanRenderer.hpp
/// @brief Vulkan command-list recorder (implements rhi::CommandList).
///
/// Translates backend-agnostic draw/bind calls into VkCmd* on the bound command
/// buffer, resolving RHI handles to Vk objects via the owning VulkanDevice.
/// Compiled only when ENGINE_HAS_VULKAN is defined. Not yet compiled/run (no SDK
/// in the authoring environment).

namespace engine::rhi::vulkan {

class VulkanDevice;

class VulkanCommandList final : public CommandList {
public:
    VulkanCommandList() = default;

    void beginRenderPass(RenderPassHandle pass, ClearColor clear) override;
    void endRenderPass() override;
    void bindPipeline(PipelineHandle pipeline) override;
    void bindVertexBuffer(BufferHandle buffer) override;
    void setViewport(const Viewport& vp) override;
    void draw(u32 vertexCount, u32 instanceCount = 1) override;

    /// Bind the device (resource tables) and the VkCommandBuffer for this frame.
    /// `vkCommandBuffer` is type-erased to keep Vulkan headers out of this header.
    void bind(VulkanDevice* device, void* vkCommandBuffer) noexcept {
        device_ = device;
        native_ = vkCommandBuffer;
    }

private:
    VulkanDevice* device_ = nullptr;
    void*         native_ = nullptr; // VkCommandBuffer
};

} // namespace engine::rhi::vulkan
