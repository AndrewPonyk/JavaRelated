/// @file VulkanRenderer.cpp
/// @brief VulkanCommandList is defined in VulkanDevice.cpp, where the device's
/// internal Vk resource tables (render pass, framebuffer, pipelines, buffers) are
/// visible — the command list resolves RHI handles against them. Keeping the
/// definitions there avoids exposing Vulkan types across a translation-unit boundary.
///
/// This file is intentionally empty (kept in the build for layout symmetry).

#if defined(ENGINE_HAS_VULKAN)
// (no definitions here — see VulkanDevice.cpp)
#endif
