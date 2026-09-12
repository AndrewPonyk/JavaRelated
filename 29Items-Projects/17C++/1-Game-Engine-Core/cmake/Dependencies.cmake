# Dependencies.cmake
# Central place to resolve third-party packages. Prefer the vcpkg manifest
# (vcpkg.json) + the preset toolchain file; find_package() then just works.
#
# Each find_package is QUIET/optional-tolerant so the tree still configures while
# modules are stubbed; real targets fail fast once a backend is actually built.

include_guard(GLOBAL)

# --- Windowing / input ---
find_package(SDL2 CONFIG QUIET)

# --- Math ---
find_package(glm CONFIG QUIET)

# --- Logging / serialization ---
find_package(spdlog CONFIG QUIET)
find_package(nlohmann_json CONFIG QUIET)

# --- Rendering backends ---
if(ENGINE_ENABLE_VULKAN)
    find_package(Vulkan QUIET)               # headers + loader
    find_package(VulkanMemoryAllocator CONFIG QUIET)
    find_package(unofficial-shaderc CONFIG QUIET)
endif()

if(ENGINE_ENABLE_OPENGL)
    find_package(OpenGL QUIET)
endif()

# --- Persistence (asset & procgen cache DB) ---
find_package(unofficial-sqlite3 CONFIG QUIET)

# --- ML inference ---
find_package(onnxruntime CONFIG QUIET)

# --- Scripting ---
find_package(Lua QUIET)
find_package(sol2 CONFIG QUIET)

# --- Testing / benchmarks (feature-gated) ---
if(ENGINE_BUILD_TESTS)
    find_package(GTest CONFIG QUIET)
endif()
if(ENGINE_BUILD_BENCHMARKS)
    find_package(benchmark CONFIG QUIET)
endif()

# Helper: warn (don't error) when an optional dep is missing during stub phase.
function(engine_require_or_warn pkg_var pkg_name)
    if(NOT ${pkg_var})
        message(WARNING
            "Dependency '${pkg_name}' not found. Stubs will still configure, but the "
            "corresponding backend will not link. Install via vcpkg (see vcpkg.json).")
    endif()
endfunction()
