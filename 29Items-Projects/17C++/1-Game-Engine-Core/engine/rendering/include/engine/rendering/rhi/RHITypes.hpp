#pragma once

#include "engine/core/Types.hpp"

/// @file RHITypes.hpp
/// @brief Backend-agnostic Render Hardware Interface vocabulary.
///
/// Opaque, typed handles + plain descriptors. Nothing here mentions Vulkan or GL —
/// the rest of the engine speaks only RHI, so backends are swappable. Concrete
/// backends map these handles to VkBuffer/GLuint/etc. in their own translation units.

namespace engine::rhi {

/// Strongly-typed opaque handle (index into a backend resource table).
template <typename Tag>
struct Handle {
    u32 id = kInvalid;
    static constexpr u32 kInvalid = 0xFFFF'FFFFu;
    [[nodiscard]] constexpr bool valid() const noexcept { return id != kInvalid; }
    friend constexpr bool operator==(Handle a, Handle b) noexcept { return a.id == b.id; }
};

struct BufferTag;
struct TextureTag;
struct ShaderTag;
struct PipelineTag;
struct RenderPassTag;

using BufferHandle     = Handle<BufferTag>;
using TextureHandle    = Handle<TextureTag>;
using ShaderHandle     = Handle<ShaderTag>;
using PipelineHandle   = Handle<PipelineTag>;
using RenderPassHandle = Handle<RenderPassTag>;

enum class BackendType : u8 { Null, Vulkan, OpenGL };

enum class BufferUsage : u8 { Vertex, Index, Uniform, Storage };
enum class Format : u8 {
    Unknown,
    R8G8B8A8_UNORM,
    R16G16B16A16_SFLOAT,
    R32G32B32_SFLOAT,
    D32_SFLOAT,
};

enum class ShaderStage : u8 { Vertex, Fragment, Compute };

struct BufferDesc {
    usize       size  = 0;
    BufferUsage usage = BufferUsage::Vertex;
    bool        hostVisible = false; // CPU-writable (uniforms) vs. device-local (static)
    const void* initialData = nullptr;
};

struct TextureDesc {
    u32    width  = 0;
    u32    height = 0;
    Format format = Format::R8G8B8A8_UNORM;
    u32    mipLevels = 1;
    bool   renderTarget = false;
};

struct ShaderDesc {
    ShaderStage stage = ShaderStage::Vertex;
    const u8*   spirv = nullptr; // compiled SPIR-V bytes
    usize       spirvSize = 0;
};

struct Viewport {
    f32 x = 0, y = 0, width = 0, height = 0, minDepth = 0, maxDepth = 1;
};

struct ClearColor {
    f32 r = 0, g = 0, b = 0, a = 1;
};

} // namespace engine::rhi
