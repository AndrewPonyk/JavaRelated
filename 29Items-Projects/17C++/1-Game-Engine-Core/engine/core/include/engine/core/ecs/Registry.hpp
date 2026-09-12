#pragma once

#include "engine/core/Assert.hpp"
#include "engine/core/Types.hpp"
#include "engine/core/ecs/ComponentPool.hpp"
#include "engine/core/ecs/Entity.hpp"
#include "engine/core/ecs/View.hpp"

#include <memory>
#include <vector>

/// @file Registry.hpp
/// @brief The ECS world: the single source of truth for entities and components.
///
/// This is the engine-domain analogue of a "backend service with CRUD endpoints":
///   - CREATE : create() / emplace<T>()
///   - READ   : get<T>() / tryGet<T>() / has<T>() / view<Ts...>()
///   - UPDATE : replace<T>() / patch<T>()
///   - DELETE : remove<T>() / destroy()
///
/// Input validation = entity-handle validation (generation check) + assertions on
/// invariants. Structural changes during iteration should be deferred (a command
/// buffer) — see docs/TECH-NOTES.md §3.6.

namespace engine::ecs {

namespace detail {
/// Monotonic component-type id generator (single definition in Registry.cpp).
u32 nextComponentTypeId();

template <typename T>
u32 componentTypeId() {
    static const u32 id = nextComponentTypeId();
    return id;
}
} // namespace detail

class Registry {
public:
    // ---- Entity lifecycle (CREATE / DELETE) -------------------------------

    /// CREATE an entity, recycling a freed slot when possible.
    [[nodiscard]] Entity create() {
        if (!freeList_.empty()) {
            const u32 index = freeList_.back();
            freeList_.pop_back();
            ++aliveCount_;
            return makeEntity(index, generations_[index]);
        }
        const auto index = static_cast<u32>(generations_.size());
        ENGINE_ASSERT(index < kEntityIndexMask, "entity index space exhausted");
        generations_.push_back(0);
        ++aliveCount_;
        return makeEntity(index, 0);
    }

    /// Validate a handle: in range and generation matches (rejects stale handles).
    [[nodiscard]] bool valid(Entity e) const noexcept {
        const u32 idx = e.index();
        return e.valid() && idx < generations_.size()
               && generations_[idx] == e.generation();
    }

    /// DELETE an entity and all its components. Bumps the generation so existing
    /// handles to it become invalid.
    void destroy(Entity e) {
        ENGINE_ASSERT(valid(e), "destroy of invalid entity {}", e.id);
        for (auto& pool : pools_) {
            if (pool && pool->contains(e)) {
                pool->remove(e);
            }
        }
        const u32 idx       = e.index();
        generations_[idx]   = (generations_[idx] + 1) & kEntityGenMask;
        freeList_.push_back(idx);
        --aliveCount_;
    }

    [[nodiscard]] usize aliveCount() const noexcept { return aliveCount_; }

    // ---- Components: CREATE / UPDATE --------------------------------------

    /// CREATE a component on an entity (construct in place).
    template <typename T, typename... Args>
    T& emplace(Entity e, Args&&... args) {
        ENGINE_ASSERT(valid(e), "emplace on invalid entity {}", e.id);
        return assurePool<T>().emplace(e, std::forward<Args>(args)...);
    }

    /// UPDATE (or insert): overwrite the component value.
    template <typename T, typename... Args>
    T& replace(Entity e, Args&&... args) {
        ENGINE_ASSERT(valid(e), "replace on invalid entity {}", e.id);
        return assurePool<T>().replace(e, std::forward<Args>(args)...);
    }

    /// UPDATE in place via a mutator callback: patch<T>(e, [](T& c){ ... }).
    template <typename T, typename Fn>
    T& patch(Entity e, Fn&& fn) {
        T& component = get<T>(e);
        std::forward<Fn>(fn)(component);
        return component;
    }

    // ---- Components: READ -------------------------------------------------

    template <typename T>
    [[nodiscard]] bool has(Entity e) const noexcept {
        const auto* pool = poolFor<T>();
        return pool != nullptr && pool->contains(e);
    }

    template <typename T>
    [[nodiscard]] T& get(Entity e) {
        ENGINE_ASSERT(has<T>(e), "get<T> on entity {} lacking component", e.id);
        return poolFor<T>()->get(e);
    }

    template <typename T>
    [[nodiscard]] T* tryGet(Entity e) {
        auto* pool = poolFor<T>();
        return pool ? pool->tryGet(e) : nullptr;
    }

    // ---- Components: DELETE ----------------------------------------------

    template <typename T>
    void remove(Entity e) {
        auto* pool = poolFor<T>();
        if (pool && pool->contains(e)) {
            pool->remove(e);
        }
    }

    // ---- Iteration (READ over sets) --------------------------------------

    /// Build a multi-component view for system iteration.
    template <typename... Ts>
    [[nodiscard]] View<Ts...> view() {
        return View<Ts...>(&assurePool<Ts>()...);
    }

    void clear() {
        pools_.clear();
        generations_.clear();
        freeList_.clear();
        aliveCount_ = 0;
    }

private:
    template <typename T>
    ComponentPool<T>& assurePool() {
        const u32 id = detail::componentTypeId<T>();
        if (id >= pools_.size()) {
            pools_.resize(id + 1);
        }
        if (!pools_[id]) {
            pools_[id] = std::make_unique<ComponentPool<T>>();
        }
        return *static_cast<ComponentPool<T>*>(pools_[id].get());
    }

    template <typename T>
    ComponentPool<T>* poolFor() {
        const u32 id = detail::componentTypeId<T>();
        if (id >= pools_.size() || !pools_[id]) {
            return nullptr;
        }
        return static_cast<ComponentPool<T>*>(pools_[id].get());
    }

    template <typename T>
    const ComponentPool<T>* poolFor() const {
        const u32 id = detail::componentTypeId<T>();
        if (id >= pools_.size() || !pools_[id]) {
            return nullptr;
        }
        return static_cast<const ComponentPool<T>*>(pools_[id].get());
    }

    std::vector<std::unique_ptr<IComponentPool>> pools_;       // indexed by component type id
    std::vector<u32>                             generations_; // per entity index
    std::vector<u32>                             freeList_;    // recycled indices
    usize                                        aliveCount_ = 0;
};

} // namespace engine::ecs
