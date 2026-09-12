#pragma once

#include "engine/core/Types.hpp"
#include "engine/rendering/rhi/RHITypes.hpp"

#include <functional>
#include <string>
#include <vector>

/// @file RenderGraph.hpp
/// @brief Declarative frame graph with real scheduling.
///
/// Passes declare the transient resources they read/write. compile() then:
///   1. builds the producer→consumer dependency DAG,
///   2. topologically orders passes (Kahn's algorithm),
///   3. culls passes whose outputs are never consumed, and
///   4. computes read-after-write barriers / layout transitions.
/// This centralizes synchronization — the antidote to manual-barrier bugs
/// (docs/TECH-NOTES.md §3.6). Backends consume barriers(); the Null backend ignores them.

namespace engine::rhi {
class CommandList;
}

namespace engine::rendering {

struct ResourceId {
    u32 value = 0xFFFF'FFFFu;
    [[nodiscard]] bool valid() const noexcept { return value != 0xFFFF'FFFFu; }
};

/// Tracked GPU resource state, used to derive transitions.
enum class GpuResourceState : u8 { Undefined, RenderTarget, ShaderRead, Present };

struct Barrier {
    u32              resource = 0;
    GpuResourceState from     = GpuResourceState::Undefined;
    GpuResourceState to       = GpuResourceState::Undefined;
};

class RenderGraph;

/// Handed to a pass's setup callback to declare its resource usage.
class RenderGraphBuilder {
public:
    RenderGraphBuilder(RenderGraph& graph, u32 passIndex) : graph_(&graph), pass_(passIndex) {}

    ResourceId createTexture(const std::string& name, const rhi::TextureDesc& desc);
    ResourceId importBackbuffer(const std::string& name = "backbuffer");
    void       read(ResourceId id);
    void       write(ResourceId id);

private:
    RenderGraph* graph_;
    u32          pass_;
};

using PassExecuteFn = std::function<void(rhi::CommandList&)>;

class RenderGraph {
public:
    /// Declare a pass: `setup` records resource usage, `execute` records GPU commands.
    void addPass(std::string name, const std::function<void(RenderGraphBuilder&)>& setup,
                 PassExecuteFn execute);

    /// Resolve dependencies, order passes, cull dead passes, compute barriers.
    void compile();

    /// Execute live passes in dependency order (recording into `cmd`).
    void execute(rhi::CommandList& cmd);

    void reset();

    // --- Introspection (used by the renderer backend + tests) ---
    [[nodiscard]] usize                    passCount() const noexcept { return passes_.size(); }
    [[nodiscard]] const std::vector<u32>&  executionOrder() const noexcept { return order_; }
    [[nodiscard]] const std::vector<Barrier>& barriers() const noexcept { return barriers_; }
    [[nodiscard]] usize                    barrierCount() const noexcept { return barriers_.size(); }
    [[nodiscard]] bool                     isLive(u32 pass) const;
    [[nodiscard]] const std::string&       passName(u32 pass) const { return passes_[pass].name; }

    // --- Builder callbacks (internal) ---
    ResourceId addResource(std::string name, bool imported);
    void       recordRead(u32 pass, u32 resource);
    void       recordWrite(u32 pass, u32 resource);

private:
    struct Pass {
        std::string      name;
        PassExecuteFn    execute;
        std::vector<u32> reads;
        std::vector<u32> writes;
        bool             alive = true;
    };
    struct Resource {
        std::string name;
        bool        imported = false;
    };

    std::vector<Pass>     passes_;
    std::vector<Resource> resources_;
    std::vector<u32>      order_;     // live pass indices in execution order
    std::vector<Barrier>  barriers_;
    bool                  compiled_ = false;
};

} // namespace engine::rendering
