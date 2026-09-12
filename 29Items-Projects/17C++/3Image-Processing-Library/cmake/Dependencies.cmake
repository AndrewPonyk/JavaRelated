# cmake/Dependencies.cmake
# Resolves OPTIONAL third-party dependencies. The core library has none beyond
# the C++ standard library and the vendored SQLite amalgamation.

# --- OpenCV (optional accelerated codec/DNN backend) ------------------------
if(IMGPROC_WITH_OPENCV)
    find_package(OpenCV CONFIG REQUIRED COMPONENTS core imgproc imgcodecs dnn)
    message(STATUS "OpenCV ${OpenCV_VERSION}")
endif()

# --- CUDA toolkit (optional) ------------------------------------------------
if(IMGPROC_WITH_CUDA)
    find_package(CUDAToolkit REQUIRED)
    message(STATUS "CUDAToolkit ${CUDAToolkit_VERSION}")
endif()

# --- pybind11 (optional) ----------------------------------------------------
if(IMGPROC_BUILD_PYTHON)
    find_package(pybind11 CONFIG REQUIRED)
endif()
