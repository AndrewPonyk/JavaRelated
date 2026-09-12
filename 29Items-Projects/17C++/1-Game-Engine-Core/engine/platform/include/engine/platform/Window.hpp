#pragma once

#include "engine/core/Result.hpp"
#include "engine/core/Types.hpp"

#include <memory>
#include <string>

/// @file Window.hpp
/// @brief SDL2-backed application window + event pump.
///
/// Owns the OS window and surface creation hooks for the renderer. The renderer
/// asks the window for the native handle / Vulkan instance extensions; the window
/// never depends on the renderer (one-way: rendering -> platform).

struct SDL_Window; // fwd-decl to keep SDL out of the public header

namespace engine::platform {

struct WindowConfig {
    std::string title  = "Game Engine Core";
    u32         width  = 1280;
    u32         height = 720;
    bool        resizable = true;
    bool        vsync     = true;
    bool        vulkan    = true; // create with Vulkan flags vs. OpenGL
};

class Window {
public:
    Window() = default;
    ~Window();

    Window(const Window&)            = delete;
    Window& operator=(const Window&) = delete;

    /// Initialize SDL + create the window. Returns an Error on failure.
    [[nodiscard]] Result<bool> create(const WindowConfig& config);
    void                       destroy();

    /// Pump OS events into the Input system. Sets the close flag on quit.
    void pollEvents();

    [[nodiscard]] bool shouldClose() const noexcept { return shouldClose_; }
    void               requestClose() noexcept { shouldClose_ = true; }

    [[nodiscard]] u32 width() const noexcept { return config_.width; }
    [[nodiscard]] u32 height() const noexcept { return config_.height; }

    /// Native handle for surface creation (cast by the active RHI backend).
    [[nodiscard]] SDL_Window* nativeHandle() const noexcept { return handle_; }

    /// For the OpenGL backend only.
    void swapBuffers();

private:
    SDL_Window*  handle_      = nullptr;
    WindowConfig config_;
    bool         shouldClose_ = false;
    bool         sdlInited_   = false;
};

} // namespace engine::platform
