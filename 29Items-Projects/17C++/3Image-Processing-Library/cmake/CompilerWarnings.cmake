# cmake/CompilerWarnings.cmake
# A strict, portable warning profile applied per-target via imgproc_set_warnings().

function(imgproc_set_warnings target)
    set(MSVC_WARNINGS /W4 /permissive- /w14640)
    set(GCC_CLANG_WARNINGS
        -Wall -Wextra -Wpedantic
        -Wshadow -Wconversion -Wsign-conversion
        -Wnon-virtual-dtor -Wold-style-cast -Wcast-align
        -Wunused -Woverloaded-virtual -Wnull-dereference
        -Wdouble-promotion -Wformat=2)

    if(MSVC)
        set(WARNINGS ${MSVC_WARNINGS})
    else()
        set(WARNINGS ${GCC_CLANG_WARNINGS})
    endif()

    if(IMGPROC_WARNINGS_AS_ERRORS)
        if(MSVC)
            list(APPEND WARNINGS /WX)
        else()
            list(APPEND WARNINGS -Werror)
        endif()
    endif()

    # Apply only to C++ (not CUDA) so nvcc isn't fed host-only flags directly.
    target_compile_options(${target} PRIVATE
        $<$<COMPILE_LANGUAGE:CXX>:${WARNINGS}>)
endfunction()
