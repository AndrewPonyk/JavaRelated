# samples — built only when the Vulkan backend is actually available (SDK present).
# Keeps the dependency-free default build untouched.

if(NOT (ENGINE_ENABLE_VULKAN AND TARGET Vulkan::Vulkan AND TARGET engine::rendering))
    message(STATUS "[samples] Vulkan backend unavailable — skipping Vulkan samples.")
    return()
endif()

# Locate glslc (ships with the Vulkan SDK) to compile GLSL -> SPIR-V.
find_program(ENGINE_GLSLC NAMES glslc
    HINTS "$ENV{VULKAN_SDK}/bin" "$ENV{VULKAN_SDK}/Bin")
if(NOT ENGINE_GLSLC)
    message(WARNING "[samples] glslc not found — skipping offscreen_triangle sample.")
    return()
endif()

set(SHADER_OUT "${CMAKE_BINARY_DIR}/shaders")
file(MAKE_DIRECTORY "${SHADER_OUT}")

set(SPV_FILES "")
foreach(stage IN ITEMS triangle.vert triangle.frag)
    set(src "${CMAKE_SOURCE_DIR}/assets/shaders/${stage}")
    set(out "${SHADER_OUT}/${stage}.spv")
    add_custom_command(
        OUTPUT "${out}"
        COMMAND "${ENGINE_GLSLC}" --target-env=vulkan1.1 "${src}" -o "${out}"
        DEPENDS "${src}"
        COMMENT "glslc: ${stage} -> SPIR-V"
        VERBATIM)
    list(APPEND SPV_FILES "${out}")
endforeach()
add_custom_target(engine_shaders DEPENDS ${SPV_FILES})

add_executable(vulkan_offscreen_triangle offscreen_triangle/main.cpp)
add_dependencies(vulkan_offscreen_triangle engine_shaders)
target_link_libraries(vulkan_offscreen_triangle
    PRIVATE engine::core engine::platform engine::rendering)
target_compile_definitions(vulkan_offscreen_triangle
    PRIVATE ENGINE_VULKAN_SHADER_DIR="${SHADER_OUT}")
