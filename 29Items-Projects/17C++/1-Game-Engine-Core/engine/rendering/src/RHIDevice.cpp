#include "engine/rendering/rhi/RHIDevice.hpp"

#include "engine/core/Log.hpp"

#include <atomic>

#if defined(ENGINE_HAS_VULKAN)
    #include "engine/rendering/vulkan/VulkanDevice.hpp"
#endif

/// @file RHIDevice.cpp
/// @brief Backend factory + a Null (headless) device used for CI/tests and as a
/// graceful fallback when no GPU backend is available.

namespace engine::rhi {
namespace {

/// No-op command list — records nothing.
class NullCommandList final : public CommandList {
public:
    void beginRenderPass(RenderPassHandle, ClearColor) override {}
    void endRenderPass() override {}
    void bindPipeline(PipelineHandle) override {}
    void bindVertexBuffer(BufferHandle) override {}
    void setViewport(const Viewport&) override {}
    void draw(u32, u32) override {}
};

/// Headless device: hands out unique handles, tracks live resources, draws nothing.
/// Resource creation is thread-safe (the renderer loads resources on worker threads).
class NullDevice final : public RHIDevice {
public:
    [[nodiscard]] BackendType backend() const noexcept override { return BackendType::Null; }

    Result<BufferHandle> createBuffer(const BufferDesc& desc) override {
        if (desc.size == 0) {
            return err<BufferHandle>(ErrorCode::InvalidArgument, "buffer size must be > 0");
        }
        liveResources_.fetch_add(1);
        return ok(BufferHandle{nextId_.fetch_add(1)});
    }
    Result<TextureHandle> createTexture(const TextureDesc& desc) override {
        if (desc.width == 0 || desc.height == 0) {
            return err<TextureHandle>(ErrorCode::InvalidArgument, "texture extent must be > 0");
        }
        liveResources_.fetch_add(1);
        return ok(TextureHandle{nextId_.fetch_add(1)});
    }
    Result<ShaderHandle> createShader(const ShaderDesc& desc) override {
        if (desc.spirv == nullptr || desc.spirvSize == 0) {
            return err<ShaderHandle>(ErrorCode::InvalidArgument, "empty SPIR-V blob");
        }
        liveResources_.fetch_add(1);
        return ok(ShaderHandle{nextId_.fetch_add(1)});
    }
    Result<PipelineHandle> createGraphicsPipeline(ShaderHandle vs, ShaderHandle fs) override {
        if (!vs.valid() || !fs.valid()) {
            return err<PipelineHandle>(ErrorCode::InvalidArgument, "invalid shader handle");
        }
        liveResources_.fetch_add(1);
        return ok(PipelineHandle{nextId_.fetch_add(1)});
    }

    void destroyBuffer(BufferHandle h) override {
        if (h.valid()) {
            liveResources_.fetch_sub(1);
        }
    }
    void destroyTexture(TextureHandle h) override {
        if (h.valid()) {
            liveResources_.fetch_sub(1);
        }
    }

    CommandList* beginFrame() override { return &cmd_; }
    void         endFrame() override {}
    void         present() override {}
    void         waitIdle() override {}
    void         onResize(u32, u32) override {}

    [[nodiscard]] u32 liveResources() const { return liveResources_.load(); }

private:
    std::atomic<u32> nextId_{0};
    std::atomic<u32> liveResources_{0};
    NullCommandList  cmd_;
};

} // namespace

Result<std::unique_ptr<RHIDevice>> createDevice(BackendType backend, platform::Window& window) {
    switch (backend) {
        case BackendType::Vulkan: {
#if defined(ENGINE_HAS_VULKAN)
            auto device = std::make_unique<vulkan::VulkanDevice>();
            const bool validation =
#if defined(NDEBUG)
                false;
#else
                true;
#endif
            auto init = device->initialize(&window, validation);
            if (!init) {
                log::error("[RHI] Vulkan init failed: {} — falling back to Null device",
                           init.error().message);
                return ok<std::unique_ptr<RHIDevice>>(std::make_unique<NullDevice>());
            }
            log::info("[RHI] Vulkan device created");
            return ok<std::unique_ptr<RHIDevice>>(std::move(device));
#else
            log::warn("[RHI] Vulkan requested but not compiled in — using Null device");
            (void) window;
            return ok<std::unique_ptr<RHIDevice>>(std::make_unique<NullDevice>());
#endif
        }
        case BackendType::OpenGL:
            // TODO: implement GLDevice (fallback backend). Null for now.
            log::warn("[RHI] OpenGL backend not yet implemented — using Null device");
            return ok<std::unique_ptr<RHIDevice>>(std::make_unique<NullDevice>());
        case BackendType::Null:
        default:
            return ok<std::unique_ptr<RHIDevice>>(std::make_unique<NullDevice>());
    }
}

} // namespace engine::rhi
