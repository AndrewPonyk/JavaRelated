# tests/CMakeLists.txt
include(FetchContent)

# Use a system/vcpkg GoogleTest if present, otherwise fetch a pinned release.
FetchContent_Declare(
    googletest
    URL https://github.com/google/googletest/archive/refs/tags/v1.15.2.tar.gz
    URL_HASH SHA256=7b42b4d6ed48810c5362c265a17faebe90dc2373c885e5216439d37927f02926
    DOWNLOAD_EXTRACT_TIMESTAMP TRUE
    FIND_PACKAGE_ARGS NAMES GTest)
set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(googletest)

# GoogleTest's own targets are exported as GTest::gtest etc. whether fetched
# or found via find_package.
if(NOT TARGET GTest::gtest_main)
    message(FATAL_ERROR "GoogleTest target not available")
endif()

add_executable(imgproc_tests
    unit/test_image.cpp
    unit/test_geometry.cpp
    unit/test_sha256.cpp
    unit/test_gaussian_blur.cpp
    unit/test_tensor.cpp
    unit/test_model_runner.cpp
    unit/test_image_io.cpp
    unit/test_object_detector.cpp
    unit/test_tracker.cpp
    unit/test_model_registry.cpp
    unit/test_onnx_runner.cpp
    integration/test_pipeline.cpp)

target_link_libraries(imgproc_tests
    PRIVATE
        imgproc::imgproc
        GTest::gtest
        GTest::gtest_main
        GTest::gmock)

target_compile_definitions(imgproc_tests PRIVATE
    IMGPROC_TEST_DATA_DIR="${CMAKE_CURRENT_SOURCE_DIR}/data")

if(IMGPROC_WITH_ONNX)
    imgproc_stage_onnxruntime(imgproc_tests)
endif()

include(GoogleTest)
gtest_discover_tests(imgproc_tests)
