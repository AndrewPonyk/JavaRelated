#pragma once

#include "engine/core/Types.hpp"
#include "engine/rendering/rhi/RHITypes.hpp"

/// @file Swapchain.hpp
/// @brief Presentation surface description shared by backends.

namespace engine::rhi {

struct SwapchainDesc {
    u32    width        = 0;
    u32    height       = 0;
    u32    imageCount   = 3;      // triple-buffered by default
    Format colorFormat  = Format::R8G8B8A8_UNORM;
    bool   vsync        = true;
};

/// Backends expose their swapchain through this minimal surface.
class ISwapchain {
public:
    virtual ~ISwapchain() = default;
    [[nodiscard]] virtual u32  imageCount() const noexcept = 0;
    [[nodiscard]] virtual u32  currentImageIndex() const noexcept = 0;
    virtual void recreate(const SwapchainDesc& desc) = 0;
};

} // namespace engine::rhi
