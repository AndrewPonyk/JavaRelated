#include "engine/platform/Window.hpp"

#include "engine/core/Log.hpp"

#if defined(ENGINE_HAS_SDL2)
    #include <SDL.h>
#endif

/// @file Window.cpp
/// @brief SDL2 window implementation. Guarded so the tree still builds headless
/// (e.g., CI without SDL): without SDL2 it logs and reports a backend error.

namespace engine::platform {

Window::~Window() {
    destroy();
}

Result<bool> Window::create(const WindowConfig& config) {
    config_ = config;

#if defined(ENGINE_HAS_SDL2)
    if (SDL_Init(SDL_INIT_VIDEO | SDL_INIT_GAMECONTROLLER) != 0) {
        return err<bool>(ErrorCode::BackendError, std::string("SDL_Init failed: ") + SDL_GetError());
    }
    sdlInited_ = true;

    Uint32 flags = SDL_WINDOW_ALLOW_HIGHDPI;
    flags |= config.vulkan ? SDL_WINDOW_VULKAN : SDL_WINDOW_OPENGL;
    if (config.resizable) {
        flags |= SDL_WINDOW_RESIZABLE;
    }

    handle_ = SDL_CreateWindow(config.title.c_str(),
                               SDL_WINDOWPOS_CENTERED, SDL_WINDOWPOS_CENTERED,
                               static_cast<int>(config.width), static_cast<int>(config.height),
                               flags);
    if (handle_ == nullptr) {
        return err<bool>(ErrorCode::BackendError,
                         std::string("SDL_CreateWindow failed: ") + SDL_GetError());
    }
    log::info("[Window] created '{}' {}x{} ({})", config.title, config.width, config.height,
              config.vulkan ? "Vulkan" : "OpenGL");
    return ok(true);
#else
    log::warn("[Window] built without SDL2 — running headless (no real window).");
    return err<bool>(ErrorCode::Unsupported, "SDL2 not available in this build");
#endif
}

void Window::destroy() {
#if defined(ENGINE_HAS_SDL2)
    if (handle_ != nullptr) {
        SDL_DestroyWindow(handle_);
        handle_ = nullptr;
    }
    if (sdlInited_) {
        SDL_Quit();
        sdlInited_ = false;
    }
#endif
}

void Window::pollEvents() {
#if defined(ENGINE_HAS_SDL2)
    SDL_Event e;
    while (SDL_PollEvent(&e) != 0) {
        switch (e.type) {
            case SDL_QUIT:
                shouldClose_ = true;
                break;
            case SDL_WINDOWEVENT:
                if (e.window.event == SDL_WINDOWEVENT_SIZE_CHANGED) {
                    config_.width  = static_cast<u32>(e.window.data1);
                    config_.height = static_cast<u32>(e.window.data2);
                    // TODO: publish WindowResized on the EventBus for the renderer.
                }
                break;
            default:
                // TODO: translate key/mouse events into platform::Input.
                break;
        }
    }
#endif
}

void Window::swapBuffers() {
#if defined(ENGINE_HAS_SDL2)
    if (!config_.vulkan && handle_ != nullptr) {
        SDL_GL_SwapWindow(handle_);
    }
#endif
}

} // namespace engine::platform
