# cmake/Onnxruntime.cmake
# Fetches the prebuilt ONNX Runtime (Windows/Linux x64) and exposes it as the
# imported target onnxruntime::onnxruntime. Included only when IMGPROC_WITH_ONNX
# is ON. To use a local install instead, set -DIMGPROC_ONNXRUNTIME_ROOT=<dir>.
include(FetchContent)

set(IMGPROC_ORT_VERSION "1.17.3" CACHE STRING "ONNX Runtime version")

if(DEFINED IMGPROC_ONNXRUNTIME_ROOT)
    set(onnxruntime_SOURCE_DIR "${IMGPROC_ONNXRUNTIME_ROOT}")
else()
    if(WIN32)
        set(_ort_url "https://github.com/microsoft/onnxruntime/releases/download/v${IMGPROC_ORT_VERSION}/onnxruntime-win-x64-${IMGPROC_ORT_VERSION}.zip")
        set(_ort_sha "356a33d024f2709786bebd5d4ca06cd5392875da95daa0455aae72edc8993256")
    elseif(UNIX AND NOT APPLE)
        set(_ort_url "https://github.com/microsoft/onnxruntime/releases/download/v${IMGPROC_ORT_VERSION}/onnxruntime-linux-x64-${IMGPROC_ORT_VERSION}.tgz")
        set(_ort_sha "")  # set IMGPROC_ORT_SHA256 to pin/verify the Linux archive
    else()
        message(FATAL_ERROR "ONNX Runtime auto-download unsupported here; set IMGPROC_ONNXRUNTIME_ROOT")
    endif()

    set(IMGPROC_ORT_SHA256 "${_ort_sha}" CACHE STRING "ONNX Runtime archive SHA256 (empty to skip)")
    if(IMGPROC_ORT_SHA256)
        FetchContent_Declare(onnxruntime URL "${_ort_url}"
            URL_HASH "SHA256=${IMGPROC_ORT_SHA256}" DOWNLOAD_EXTRACT_TIMESTAMP TRUE)
    else()
        FetchContent_Declare(onnxruntime URL "${_ort_url}" DOWNLOAD_EXTRACT_TIMESTAMP TRUE)
    endif()
    FetchContent_MakeAvailable(onnxruntime)
endif()

set(_ort_inc "${onnxruntime_SOURCE_DIR}/include")
if(NOT EXISTS "${_ort_inc}/onnxruntime_cxx_api.h")
    message(FATAL_ERROR "ONNX Runtime headers not found in ${_ort_inc}")
endif()

add_library(onnxruntime::onnxruntime SHARED IMPORTED GLOBAL)
set_target_properties(onnxruntime::onnxruntime PROPERTIES
    INTERFACE_INCLUDE_DIRECTORIES "${_ort_inc}")

if(WIN32)
    set_target_properties(onnxruntime::onnxruntime PROPERTIES
        IMPORTED_LOCATION "${onnxruntime_SOURCE_DIR}/lib/onnxruntime.dll"
        IMPORTED_IMPLIB   "${onnxruntime_SOURCE_DIR}/lib/onnxruntime.lib")
    set(IMGPROC_ORT_RUNTIME "${onnxruntime_SOURCE_DIR}/lib/onnxruntime.dll"
        CACHE INTERNAL "ONNX Runtime shared library to stage next to executables")
else()
    set_target_properties(onnxruntime::onnxruntime PROPERTIES
        IMPORTED_LOCATION "${onnxruntime_SOURCE_DIR}/lib/libonnxruntime.so")
    set(IMGPROC_ORT_RUNTIME "" CACHE INTERNAL "")
endif()

message(STATUS "ONNX Runtime: ${onnxruntime_SOURCE_DIR}")

# Stage the ONNX Runtime shared library next to `target`'s executable so it can
# be found at run time (Windows has no RPATH; the .dll must sit beside the .exe).
function(imgproc_stage_onnxruntime target)
    if(IMGPROC_ORT_RUNTIME)
        add_custom_command(TARGET ${target} POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E copy_if_different
                    "${IMGPROC_ORT_RUNTIME}" "$<TARGET_FILE_DIR:${target}>"
            VERBATIM)
    endif()
endfunction()
