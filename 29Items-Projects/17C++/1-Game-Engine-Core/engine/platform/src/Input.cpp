#include "engine/platform/Input.hpp"

/// @file Input.cpp
/// @brief Polled input state with edge detection between frames.

namespace engine::platform {

void Input::beginFrame() {
    prevKeys_     = keys_;
    prevMousePos_ = mousePos_;
    scroll_       = 0.0f;
}

void Input::setKey(Key key, bool down) {
    keys_[static_cast<usize>(key)] = down;
}

void Input::setMouseButton(MouseButton b, bool down) {
    buttons_[static_cast<usize>(b)] = down;
}

void Input::setMousePosition(f32 x, f32 y) {
    mousePos_ = {x, y};
}

void Input::addScroll(f32 delta) {
    scroll_ += delta;
}

bool Input::isKeyDown(Key key) const {
    return keys_[static_cast<usize>(key)];
}

bool Input::wasKeyPressed(Key key) const {
    const auto i = static_cast<usize>(key);
    return keys_[i] && !prevKeys_[i];
}

bool Input::wasKeyReleased(Key key) const {
    const auto i = static_cast<usize>(key);
    return !keys_[i] && prevKeys_[i];
}

bool Input::isMouseDown(MouseButton b) const {
    return buttons_[static_cast<usize>(b)];
}

} // namespace engine::platform
