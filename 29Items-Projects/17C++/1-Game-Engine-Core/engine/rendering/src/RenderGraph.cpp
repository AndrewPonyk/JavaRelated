#include "engine/rendering/RenderGraph.hpp"

#include "engine/rendering/rhi/RHIDevice.hpp"

#include <algorithm>
#include <queue>
#include <unordered_set>

/// @file RenderGraph.cpp
/// @brief Frame-graph compilation: dependency DAG, topological order (Kahn),
/// dead-pass culling, and read-after-write barrier derivation.

namespace engine::rendering {

// ---- Builder ----
ResourceId RenderGraphBuilder::createTexture(const std::string& name, const rhi::TextureDesc&) {
    return graph_->addResource(name, /*imported*/ false);
}
ResourceId RenderGraphBuilder::importBackbuffer(const std::string& name) {
    return graph_->addResource(name, /*imported*/ true);
}
void RenderGraphBuilder::read(ResourceId id) {
    if (id.valid()) {
        graph_->recordRead(pass_, id.value);
    }
}
void RenderGraphBuilder::write(ResourceId id) {
    if (id.valid()) {
        graph_->recordWrite(pass_, id.value);
    }
}

// ---- Graph construction ----
ResourceId RenderGraph::addResource(std::string name, bool imported) {
    const auto id = static_cast<u32>(resources_.size());
    resources_.push_back({std::move(name), imported});
    return ResourceId{id};
}
void RenderGraph::recordRead(u32 pass, u32 resource) {
    passes_[pass].reads.push_back(resource);
}
void RenderGraph::recordWrite(u32 pass, u32 resource) {
    passes_[pass].writes.push_back(resource);
}

void RenderGraph::addPass(std::string name,
                          const std::function<void(RenderGraphBuilder&)>& setup,
                          PassExecuteFn execute) {
    const auto passIndex = static_cast<u32>(passes_.size());
    passes_.push_back(Pass{std::move(name), std::move(execute), {}, {}, true});
    RenderGraphBuilder builder(*this, passIndex);
    setup(builder); // collect this pass's resource usage
    compiled_ = false;
}

bool RenderGraph::isLive(u32 pass) const {
    return pass < passes_.size() && passes_[pass].alive;
}

void RenderGraph::compile() {
    order_.clear();
    barriers_.clear();
    const auto n = static_cast<u32>(passes_.size());
    if (n == 0) {
        compiled_ = true;
        return;
    }

    // 1) Dead-pass culling (reverse pass): a pass is live if it has side effects
    //    (no declared writes), writes an imported resource, or one of its writes is
    //    read by a live downstream pass. Propagate "needed" resources upstream.
    std::unordered_set<u32> needed;
    for (u32 i = n; i-- > 0;) {
        Pass& p   = passes_[i];
        bool  live = p.writes.empty();
        for (const u32 w : p.writes) {
            if (resources_[w].imported || needed.count(w) != 0) {
                live = true;
            }
        }
        p.alive = live;
        if (live) {
            for (const u32 r : p.reads) {
                needed.insert(r);
            }
        }
    }

    // 2) Dependency edges: a reader depends on the most recent prior writer of a
    //    resource. Among live passes only.
    std::vector<std::vector<u32>> adjacency(n);
    std::vector<u32>              indegree(n, 0);
    std::vector<int>             lastWriter(resources_.size(), -1);
    for (u32 p = 0; p < n; ++p) {
        if (!passes_[p].alive) {
            continue;
        }
        for (const u32 r : passes_[p].reads) {
            const int w = lastWriter[r];
            if (w >= 0 && static_cast<u32>(w) != p) {
                adjacency[static_cast<u32>(w)].push_back(p);
                ++indegree[p];
                barriers_.push_back({r, GpuResourceState::RenderTarget, GpuResourceState::ShaderRead});
            }
        }
        for (const u32 w : passes_[p].writes) {
            lastWriter[w] = static_cast<int>(p);
        }
    }

    // 3) Kahn topological sort with stable (lowest-index-first) tie-breaking.
    auto cmp = [](u32 a, u32 b) { return a > b; };
    std::priority_queue<u32, std::vector<u32>, decltype(cmp)> ready(cmp);
    for (u32 p = 0; p < n; ++p) {
        if (passes_[p].alive && indegree[p] == 0) {
            ready.push(p);
        }
    }
    while (!ready.empty()) {
        const u32 p = ready.top();
        ready.pop();
        order_.push_back(p);
        for (const u32 next : adjacency[p]) {
            if (--indegree[next] == 0) {
                ready.push(next);
            }
        }
    }

    // Final transition: present the backbuffer after its last writer.
    for (u32 ri = 0; ri < resources_.size(); ++ri) {
        if (resources_[ri].imported && lastWriter[ri] >= 0) {
            barriers_.push_back({ri, GpuResourceState::RenderTarget, GpuResourceState::Present});
        }
    }

    compiled_ = true;
}

void RenderGraph::execute(rhi::CommandList& cmd) {
    if (!compiled_) {
        compile();
    }
    for (const u32 p : order_) {
        // Backends would issue passes_[p] barriers here; the Null backend ignores them.
        if (passes_[p].execute) {
            passes_[p].execute(cmd);
        }
    }
}

void RenderGraph::reset() {
    passes_.clear();
    resources_.clear();
    order_.clear();
    barriers_.clear();
    compiled_ = false;
}

} // namespace engine::rendering
