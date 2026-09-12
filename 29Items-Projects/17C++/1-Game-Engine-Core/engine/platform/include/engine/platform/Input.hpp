#pragma once

#include "engine/core/Types.hpp"
#include "engine/core/math/Vec.hpp"

#include <array>

/// @file Input.hpp
/// @brief Polled input state, updated once per frame by Window::pollEvents().
///
/// Systems read this directly (or via an InputComponent mirror). Kept as a flat,
/// trivially-copyable snapshot — data-oriented and easy to record/replay.

namespace engine::platform {

enum class Key : u16 {
    Unknown = 0,
    W, A, S, D,
    Space, Escape, Enter, LeftShift,
    Up, Down, Left, Right,
    Count
};

enum class MouseButton : u8 { Left, Right, Middle, Count };

class Input {
public:
    void beginFrame();                 // roll "current" -> "previous"
    void setKey(Key key, bool down);
    void setMouseButton(MouseButton b, bool down);
    void setMousePosition(f32 x, f32 y);
    void addScroll(f32 delta);

    [[nodiscard]] bool isKeyDown(Key key) const;
    [[nodiscard]] bool wasKeyPressed(Key key) const;   // edge: up -> down this frame
    [[nodiscard]] bool wasKeyReleased(Key key) const;  // edge: down -> up this frame
    [[nodiscard]] bool isMouseDown(MouseButton b) const;

    [[nodiscard]] math::Vec2 mousePosition() const noexcept { return mousePos_; }
    [[nodiscard]] math::Vec2 mouseDelta() const noexcept {
        return {mousePos_.x - prevMousePos_.x, mousePos_.y - prevMousePos_.y};
    }
    [[nodiscard]] f32 scrollDelta() const noexcept { return scroll_; }

private:
    static constexpr usize kKeyCount    = static_cast<usize>(Key::Count);
    static constexpr usize kButtonCount = static_cast<usize>(MouseButton::Count);

    std::array<bool, kKeyCount>    keys_{};
    std::array<bool, kKeyCount>    prevKeys_{};
    std::array<bool, kButtonCount> buttons_{};
    math::Vec2                     mousePos_{};
    math::Vec2                     prevMousePos_{};
    f32                            scroll_ = 0.0f;
};

} // namespace engine::platform
