#include "engine/rendering/Renderer.hpp"

#include "engine/core/Log.hpp"
#include "engine/core/jobs/JobSystem.hpp"
#include "engine/platform/Filesystem.hpp"
#include "engine/platform/Window.hpp"
#include "engine/rendering/RenderGraph.hpp"

#include <memory>
#include <string>
#include <vector>

/// @file Renderer.cpp
/// @brief High-level renderer: device lifecycle as an explicit state machine, plus
/// async ("fetched") GPU resources that expose loading/loaded/errored states and a
/// graceful placeholder on failure. Mirrors a frontend component's data-load UX.

namespace engine::rendering {

struct Renderer::Impl {
    struct Renderable {
        std::string                assetPath;
        std::atomic<ResourceState> state{ResourceState::Idle};
        rhi::BufferHandle          vertexBuffer{};
    };

    std::vector<std::unique_ptr<Renderable>> renderables;
    RenderGraph                              graph;
};

Renderer::Renderer() = default;

Renderer::~Renderer() {
    shutdown();
}

void Renderer::transition(RendererState next) {
    state_.store(next);
}

Result<bool> Renderer::initialize(platform::Window& window, jobs::JobSystem& jobs,
                                  rhi::BackendType backend) {
    transition(RendererState::Initializing);
    window_ = &window;
    jobs_   = &jobs;
    impl_   = std::make_unique<Impl>();

    auto deviceResult = rhi::createDevice(backend, window);
    if (!deviceResult) {
        log::error("[Renderer] device creation failed: {}", deviceResult.error().message);
        transition(RendererState::Failed);
        return deviceResult.error();
    }
    device_ = std::move(deviceResult.value());
    transition(RendererState::Ready);
    log::info("[Renderer] ready (backend id = {})", static_cast<int>(device_->backend()));
    return ok(true);
}

void Renderer::shutdown() {
    if (device_) {
        device_->waitIdle();
    }
    impl_.reset();
    device_.reset();
    transition(RendererState::Uninitialized);
}

RenderableHandle Renderer::loadRenderableAsync(std::string assetPath) {
    if (!impl_) {
        return {};
    }
    auto record       = std::make_unique<Impl::Renderable>();
    record->assetPath = std::move(assetPath);
    record->state.store(ResourceState::Loading);

    const auto id = static_cast<u32>(impl_->renderables.size());
    impl_->renderables.push_back(std::move(record));
    Impl::Renderable* rec = impl_->renderables.back().get();

    // "Data fetching" off the main thread: read the asset, then create GPU buffers.
    jobs_->dispatch([this, rec] {
        const auto path  = platform::fs::resolveAsset(rec->assetPath);
        auto       bytes = platform::fs::readBytes(path);
        if (!bytes) {
            log::warn("[Renderer] load failed for '{}': {}", rec->assetPath, bytes.error().message);
            rec->state.store(ResourceState::Errored);
            return;
        }
        // TODO: parse mesh -> create vertex/index buffers via device_.
        rhi::BufferDesc desc{};
        desc.size  = bytes.value().size();
        desc.usage = rhi::BufferUsage::Vertex;
        auto buffer = device_->createBuffer(desc);
        if (!buffer) {
            rec->state.store(ResourceState::Errored);
            return;
        }
        rec->vertexBuffer = buffer.value();
        rec->state.store(ResourceState::Loaded);
        log::debug("[Renderer] loaded renderable '{}'", rec->assetPath);
    });

    return RenderableHandle{id};
}

ResourceState Renderer::resourceState(RenderableHandle handle) const {
    if (!impl_ || handle.id >= impl_->renderables.size()) {
        return ResourceState::Idle;
    }
    return impl_->renderables[handle.id]->state.load();
}

void Renderer::renderFrame() {
    // Display pattern: never crash if not Ready — just skip the frame.
    if (state_.load() != RendererState::Ready || !device_) {
        return;
    }

    rhi::CommandList* cmd = device_->beginFrame();
    if (cmd == nullptr) {
        transition(RendererState::DeviceLost);
        return;
    }

    cmd->beginRenderPass(rhi::RenderPassHandle{}, rhi::ClearColor{0.05f, 0.05f, 0.08f, 1.0f});
    for (const auto& r : impl_->renderables) {
        switch (r->state.load()) {
            case ResourceState::Loaded:
                cmd->bindVertexBuffer(r->vertexBuffer);
                cmd->draw(/*vertexCount*/ 3);
                break;
            case ResourceState::Loading:
            case ResourceState::Errored:
                // TODO: draw a magenta placeholder so missing/loading assets are visible.
                break;
            case ResourceState::Idle:
                break;
        }
    }
    cmd->endRenderPass();

    device_->endFrame();
    device_->present();
}

void Renderer::onResize(u32 width, u32 height) {
    if (device_) {
        device_->onResize(width, height);
    }
}

} // namespace engine::rendering
