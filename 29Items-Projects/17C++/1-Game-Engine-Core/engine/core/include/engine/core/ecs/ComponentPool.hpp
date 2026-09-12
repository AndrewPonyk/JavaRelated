#pragma once

#include "engine/core/Types.hpp"
#include "engine/core/ecs/Entity.hpp"
#include "engine/core/ecs/SparseSet.hpp"

#include <utility>
#include <vector>

/// @file ComponentPool.hpp
/// @brief Typed, dense component storage keyed by entity.
///
/// `components_` runs parallel to the SparseSet's dense entity array, so iterating
/// either is a contiguous scan (the data-oriented payoff). A type-erased base lets
/// the Registry hold heterogeneous pools without templating the Registry itself.

namespace engine::ecs {

/// Type-erased interface so Registry can store pools of any component type.
class IComponentPool {
public:
    virtual ~IComponentPool() = default;
    [[nodiscard]] virtual bool contains(Entity e) const noexcept = 0;
    virtual void remove(Entity e) = 0;
    [[nodiscard]] virtual const SparseSet& sparseSet() const noexcept = 0;
};

template <typename T>
class ComponentPool final : public IComponentPool {
public:
    /// Construct-in-place. Caller guarantees the entity has no T yet.
    template <typename... Args>
    T& emplace(Entity e, Args&&... args) {
        set_.insert(e);
        return components_.emplace_back(std::forward<Args>(args)...);
    }

    /// Overwrite if present, otherwise insert.
    template <typename... Args>
    T& replace(Entity e, Args&&... args) {
        if (set_.contains(e)) {
            T& slot = components_[set_.denseIndex(e)];
            slot    = T{std::forward<Args>(args)...};
            return slot;
        }
        return emplace(e, std::forward<Args>(args)...);
    }

    [[nodiscard]] bool contains(Entity e) const noexcept override { return set_.contains(e); }

    [[nodiscard]] T& get(Entity e) { return components_[set_.denseIndex(e)]; }
    [[nodiscard]] const T& get(Entity e) const { return components_[set_.denseIndex(e)]; }

    [[nodiscard]] T* tryGet(Entity e) {
        return set_.contains(e) ? &components_[set_.denseIndex(e)] : nullptr;
    }

    /// Swap-and-pop, mirroring the SparseSet so dense arrays stay aligned.
    void remove(Entity e) override {
        const u32 removedPos = set_.remove(e);
        const auto lastPos   = components_.size() - 1;
        if (removedPos != lastPos) {
            components_[removedPos] = std::move(components_[lastPos]);
        }
        components_.pop_back();
    }

    [[nodiscard]] const SparseSet& sparseSet() const noexcept override { return set_; }

    [[nodiscard]] std::vector<T>&       data() noexcept { return components_; }
    [[nodiscard]] const std::vector<T>& data() const noexcept { return components_; }
    [[nodiscard]] usize size() const noexcept { return components_.size(); }

private:
    SparseSet      set_;
    std::vector<T> components_; // parallel to set_.entities()
};

} // namespace engine::ecs
