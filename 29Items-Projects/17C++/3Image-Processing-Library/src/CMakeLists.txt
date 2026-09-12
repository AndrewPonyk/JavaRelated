# src/CMakeLists.txt — the core imgproc library target.

set(IMGPROC_SOURCES
    core/image.cpp
    core/tensor.cpp
    core/geometry.cpp
    cuda/cuda_utils.cpp
    filters/gaussian_blur.cpp
    detection/object_detector.cpp
    tracking/tracker.cpp
    ml/model_runner.cpp
    io/image_io.cpp
    registry/model_registry.cpp
    util/sha256.cpp)

# CUDA translation units are added only when CUDA is enabled.
if(IMGPROC_WITH_CUDA)
    list(APPEND IMGPROC_SOURCES filters/gaussian_blur.cu)
endif()

# ONNX Runtime backend is added only when enabled.
if(IMGPROC_WITH_ONNX)
    list(APPEND IMGPROC_SOURCES ml/onnx_runner.cpp)
endif()

add_library(imgproc ${IMGPROC_SOURCES})
add_library(imgproc::imgproc ALIAS imgproc)

target_include_directories(imgproc
    PUBLIC
        $<BUILD_INTERFACE:${CMAKE_SOURCE_DIR}/include>
        $<BUILD_INTERFACE:${CMAKE_BINARY_DIR}/generated>
        $<INSTALL_INTERFACE:include>)

# SQLite is an implementation detail (registry backend) — link PRIVATE.
target_link_libraries(imgproc PRIVATE sqlite3)

if(IMGPROC_WITH_OPENCV)
    target_compile_definitions(imgproc PUBLIC IMGPROC_WITH_OPENCV)
    target_link_libraries(imgproc PUBLIC ${OpenCV_LIBS})
endif()

if(IMGPROC_WITH_ONNX)
    target_compile_definitions(imgproc PUBLIC IMGPROC_WITH_ONNX)
    target_link_libraries(imgproc PRIVATE onnxruntime::onnxruntime)
endif()

if(IMGPROC_WITH_CUDA)
    target_compile_definitions(imgproc PUBLIC IMGPROC_WITH_CUDA)
    target_link_libraries(imgproc PUBLIC CUDA::cudart)
    set_target_properties(imgproc PROPERTIES
        CUDA_SEPARABLE_COMPILATION ON
        CUDA_ARCHITECTURES "${IMGPROC_CUDA_ARCHITECTURES}")
endif()

imgproc_set_warnings(imgproc)

install(TARGETS imgproc EXPORT imgprocTargets
        ARCHIVE DESTINATION lib
        LIBRARY DESTINATION lib
        RUNTIME DESTINATION bin)
install(DIRECTORY ${CMAKE_SOURCE_DIR}/include/ DESTINATION include)
