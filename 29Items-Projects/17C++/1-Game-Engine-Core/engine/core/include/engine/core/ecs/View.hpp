#pragma once

#include "engine/core/Types.hpp"
#include "engine/core/ecs/ComponentPool.hpp"
#include "engine/core/ecs/Entity.hpp"
#include "engine/core/ecs/SparseSet.hpp"

#include <limits>
#include <tuple>

/// @file View.hpp
/// @brief Multi-component iteration over the Registry.
///
/// Iteration is driven by the *smallest* matching pool, then membership in the
/// other pools is checked — minimizing work and keeping the common case a linear
/// scan over a dense array.

namespace engine::ecs {

template <typename... Ts>
class View {
    static_assert(sizeof...(Ts) >= 1, "View requires at least one component type");

public:
    explicit View(ComponentPool<Ts>*... pools) : pools_(pools...) {}

    /// Invoke `fn(Entity, Ts&...)` for every entity owning all of Ts.
    template <typename Fn>
    void each(Fn&& fn) {
        const SparseSet& driver = smallestSet();
        // Copy avoids issues if fn reads the same set; structural edits must defer.
        for (Entity e : driver.entities()) {
            if ((std::get<ComponentPool<Ts>*>(pools_)->contains(e) && ...)) {
                fn(e, std::get<ComponentPool<Ts>*>(pools_)->get(e)...);
            }
        }
    }

    /// Upper bound on iterations (size of the driving pool).
    [[nodiscard]] usize sizeHint() const { return smallestSet().size(); }

private:
    [[nodiscard]] const SparseSet& smallestSet() const {
        const SparseSet* best = nullptr;
        usize            bestSize = std::numeric_limits<usize>::max();
        auto consider = [&](const IComponentPool* p) {
            if (p->sparseSet().size() < bestSize) {
                bestSize = p->sparseSet().size();
                best     = &p->sparseSet();
            }
        };
        (consider(std::get<ComponentPool<Ts>*>(pools_)), ...);
        return *best;
    }

    std::tuple<ComponentPool<Ts>*...> pools_;
};

} // namespace engine::ecs
