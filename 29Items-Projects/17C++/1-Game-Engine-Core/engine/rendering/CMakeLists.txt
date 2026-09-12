# engine/rendering — RHI abstraction + Vulkan backend (the presentation layer).

add_library(engine_rendering
    src/RHIDevice.cpp
    src/Renderer.cpp
    src/RenderGraph.cpp
    src/vulkan/VulkanDevice.cpp
    src/vulkan/VulkanRenderer.cpp
)
add_library(engine::rendering ALIAS engine_rendering)

target_include_directories(engine_rendering
    PUBLIC $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
)

target_link_libraries(engine_rendering
    PUBLIC  engine::core engine::platform
    PRIVATE engine_project_options
)

if(ENGINE_ENABLE_VULKAN AND TARGET Vulkan::Vulkan)
    target_link_libraries(engine_rendering PRIVATE Vulkan::Vulkan)
    target_compile_definitions(engine_rendering PRIVATE ENGINE_HAS_VULKAN=1)
    if(TARGET GPUOpen::VulkanMemoryAllocator)
        target_link_libraries(engine_rendering PRIVATE GPUOpen::VulkanMemoryAllocator)
    endif()
else()
    message(WARNING "[rendering] Vulkan not enabled/found — using the Null backend.")
endif()

if(ENGINE_ENABLE_OPENGL AND TARGET OpenGL::GL)
    target_link_libraries(engine_rendering PRIVATE OpenGL::GL)
    target_compile_definitions(engine_rendering PRIVATE ENGINE_HAS_OPENGL=1)
endif()
