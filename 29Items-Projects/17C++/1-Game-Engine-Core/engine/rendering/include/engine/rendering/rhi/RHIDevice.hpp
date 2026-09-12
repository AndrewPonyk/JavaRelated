#pragma once

#include "engine/core/Result.hpp"
#include "engine/rendering/rhi/RHITypes.hpp"

#include <memory>

/// @file RHIDevice.hpp
/// @brief The backend-agnostic GPU device interface.
///
/// Resource creation + command submission, expressed without any graphics-API type.
/// `createDevice()` is the factory that selects a concrete backend (Vulkan/GL/Null).

namespace engine::platform {
class Window;
}

namespace engine::rhi {

/// Records GPU commands for a frame (recorded on the main or worker threads).
class CommandList {
public:
    virtual ~CommandList() = default;
    virtual void beginRenderPass(RenderPassHandle pass, ClearColor clear) = 0;
    virtual void endRenderPass()                                          = 0;
    virtual void bindPipeline(PipelineHandle pipeline)                    = 0;
    virtual void bindVertexBuffer(BufferHandle buffer)                    = 0;
    virtual void setViewport(const Viewport& vp)                          = 0;
    virtual void draw(u32 vertexCount, u32 instanceCount = 1)             = 0;
};

class RHIDevice {
public:
    virtual ~RHIDevice() = default;

    [[nodiscard]] virtual BackendType backend() const noexcept = 0;

    // --- Resource creation (each returns an opaque handle or an Error) ---
    [[nodiscard]] virtual Result<BufferHandle>   createBuffer(const BufferDesc& desc)   = 0;
    [[nodiscard]] virtual Result<TextureHandle>  createTexture(const TextureDesc& desc) = 0;
    [[nodiscard]] virtual Result<ShaderHandle>   createShader(const ShaderDesc& desc)   = 0;
    [[nodiscard]] virtual Result<PipelineHandle> createGraphicsPipeline(ShaderHandle vs,
                                                                        ShaderHandle fs) = 0;

    virtual void destroyBuffer(BufferHandle handle)   = 0;
    virtual void destroyTexture(TextureHandle handle) = 0;

    // --- Frame lifecycle ---
    [[nodiscard]] virtual CommandList* beginFrame() = 0;
    virtual void                       endFrame()   = 0;
    virtual void                       present()    = 0;
    virtual void                       waitIdle()   = 0;

    /// Recreate the swapchain after a resize.
    virtual void onResize(u32 width, u32 height) = 0;
};

/// Factory: create a device for the requested backend bound to a window.
[[nodiscard]] Result<std::unique_ptr<RHIDevice>> createDevice(BackendType backend,
                                                              platform::Window& window);

} // namespace engine::rhi
