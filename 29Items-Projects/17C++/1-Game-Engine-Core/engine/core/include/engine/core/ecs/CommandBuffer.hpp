#pragma once

#include "engine/core/Types.hpp"
#include "engine/core/ecs/Registry.hpp"

#include <functional>
#include <vector>

/// @file CommandBuffer.hpp
/// @brief Deferred structural changes for safe mutation during iteration.
///
/// Adding/removing components or destroying entities *while iterating a View* can
/// reallocate component pools and invalidate the iteration. Systems record such
/// changes here and flush() them at a safe point (end of the system/frame). This is
/// the mitigation documented in docs/TECH-NOTES.md §3.6.

namespace engine::ecs {

class CommandBuffer {
public:
    /// Queue creation of an entity; `onCreate` receives the new entity when flushed.
    void create(std::function<void(Registry&, Entity)> onCreate) {
        commands_.emplace_back([fn = std::move(onCreate)](Registry& r) {
            const Entity e = r.create();
            if (fn) {
                fn(r, e);
            }
        });
    }

    /// Queue destruction of an existing entity.
    void destroy(Entity e) {
        commands_.emplace_back([e](Registry& r) {
            if (r.valid(e)) {
                r.destroy(e);
            }
        });
    }

    /// Queue adding/overwriting a component value.
    template <typename T>
    void emplace(Entity e, T value) {
        commands_.emplace_back([e, v = std::move(value)](Registry& r) mutable {
            if (r.valid(e)) {
                r.replace<T>(e, std::move(v));
            }
        });
    }

    /// Queue removal of a component.
    template <typename T>
    void remove(Entity e) {
        commands_.emplace_back([e](Registry& r) {
            if (r.valid(e)) {
                r.remove<T>(e);
            }
        });
    }

    /// Apply all queued commands in order, then clear.
    void flush(Registry& registry) {
        for (auto& cmd : commands_) {
            cmd(registry);
        }
        commands_.clear();
    }

    [[nodiscard]] usize pending() const noexcept { return commands_.size(); }
    [[nodiscard]] bool  empty() const noexcept { return commands_.empty(); }

private:
    std::vector<std::function<void(Registry&)>> commands_;
};

} // namespace engine::ecs
