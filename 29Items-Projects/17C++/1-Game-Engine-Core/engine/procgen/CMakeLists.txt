# engine/procgen — ML-based procedural terrain generation.

add_library(engine_procgen
    src/TerrainModel.cpp
    src/TerrainGenerator.cpp
)
add_library(engine::procgen ALIAS engine_procgen)

target_include_directories(engine_procgen
    PUBLIC $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
)

target_link_libraries(engine_procgen
    PUBLIC  engine::core
    PRIVATE engine_project_options engine::resources
)

# ONNX Runtime is optional; without it, a deterministic procedural fallback is used.
if(TARGET onnxruntime::onnxruntime)
    target_link_libraries(engine_procgen PRIVATE onnxruntime::onnxruntime)
    target_compile_definitions(engine_procgen PRIVATE ENGINE_HAS_ONNX=1)
else()
    message(WARNING "[procgen] ONNX Runtime not found — using procedural noise fallback.")
endif()
