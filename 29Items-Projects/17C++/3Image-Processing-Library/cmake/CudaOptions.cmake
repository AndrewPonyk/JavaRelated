# cmake/CudaOptions.cmake
# Centralizes CUDA architecture selection. Override on the command line:
#   -DIMGPROC_CUDA_ARCHITECTURES="75;86;89"
# or use "native" to target the building machine's GPU.

if(IMGPROC_WITH_CUDA)
    if(NOT DEFINED IMGPROC_CUDA_ARCHITECTURES)
        # Turing(75), Ampere(86), Ada(89). Tune to your deployment fleet.
        set(IMGPROC_CUDA_ARCHITECTURES "75;86;89"
            CACHE STRING "CUDA architectures to compile for")
    endif()
    message(STATUS "CUDA architectures: ${IMGPROC_CUDA_ARCHITECTURES}")
endif()
