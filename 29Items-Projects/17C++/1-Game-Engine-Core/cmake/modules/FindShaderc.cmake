# FindShaderc.cmake
# Minimal locator for the `glslc` shader compiler (ships with the Vulkan SDK or
# shaderc). Used by tools/scripts/compile_shaders.sh and the shader build step.
#
# Defines:
#   Shaderc_FOUND        - whether glslc was located
#   Shaderc_GLSLC        - path to the glslc executable
#   Shaderc::glslc       - imported executable target (if found)

find_program(Shaderc_GLSLC
    NAMES glslc
    HINTS
        $ENV{VULKAN_SDK}/bin
        $ENV{VULKAN_SDK}/Bin
    DOC "Path to the glslc GLSL->SPIR-V compiler"
)

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(Shaderc
    REQUIRED_VARS Shaderc_GLSLC
)

if(Shaderc_FOUND AND NOT TARGET Shaderc::glslc)
    add_executable(Shaderc::glslc IMPORTED)
    set_target_properties(Shaderc::glslc PROPERTIES IMPORTED_LOCATION "${Shaderc_GLSLC}")
endif()

mark_as_advanced(Shaderc_GLSLC)
