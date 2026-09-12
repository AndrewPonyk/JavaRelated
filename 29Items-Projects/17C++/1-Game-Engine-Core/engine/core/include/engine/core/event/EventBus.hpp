#pragma once

#include "engine/core/Types.hpp"

#include <functional>
#include <typeindex>
#include <unordered_map>
#include <vector>

/// @file EventBus.hpp
/// @brief Type-safe, in-process publish/subscribe for decoupled notifications.
///
/// Used for cross-cutting events (WindowResized, AssetLoaded, CollisionBegan) where
/// publishers must not know subscribers. Events are plain structs. Not for hot
/// per-frame data — that goes through the Registry directly.

namespace engine::event {

class EventBus {
public:
    template <typename E>
    using Handler = std::function<void(const E&)>;

    /// Subscribe to events of type E. Returns a token usable to unsubscribe.
    template <typename E>
    u64 subscribe(Handler<E> handler) {
        auto& list = handlers_[std::type_index(typeid(E))];
        const u64 token = nextToken_++;
        list.push_back({token, [h = std::move(handler)](const void* e) {
                            h(*static_cast<const E*>(e));
                        }});
        return token;
    }

    /// Synchronously dispatch an event to all current subscribers.
    template <typename E>
    void publish(const E& event) const {
        const auto it = handlers_.find(std::type_index(typeid(E)));
        if (it == handlers_.end()) {
            return;
        }
        for (const auto& entry : it->second) {
            entry.fn(&event);
        }
    }

    template <typename E>
    void unsubscribe(u64 token) {
        const auto it = handlers_.find(std::type_index(typeid(E)));
        if (it == handlers_.end()) {
            return;
        }
        auto& list = it->second;
        std::erase_if(list, [token](const Entry& e) { return e.token == token; });
    }

private:
    struct Entry {
        u64                              token;
        std::function<void(const void*)> fn;
    };
    std::unordered_map<std::type_index, std::vector<Entry>> handlers_;
    u64                                                     nextToken_ = 1;
};

} // namespace engine::event
