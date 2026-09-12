# CompilerWarnings.cmake
# Per-compiler warning sets + optional sanitizers, applied to an INTERFACE target.

function(engine_set_project_warnings target)
    set(MSVC_WARNINGS
        /W4         # high warning level
        /permissive- # standards conformance
        /w14242 /w14254 /w14263 /w14265 /w14287
        /w14296 /w14311 /w14545 /w14546 /w14547
        /w14549 /w14555 /w14619 /w14640 /w14826
        /w14905 /w14906 /w14928
    )

    set(CLANG_WARNINGS
        -Wall
        -Wextra
        -Wpedantic
        -Wshadow
        -Wnon-virtual-dtor
        -Wold-style-cast
        -Wcast-align
        -Wunused
        -Woverloaded-virtual
        -Wconversion
        -Wsign-conversion
        -Wnull-dereference
        -Wdouble-promotion
        -Wformat=2
        -Wimplicit-fallthrough
    )

    set(GCC_WARNINGS
        ${CLANG_WARNINGS}
        -Wmisleading-indentation
        -Wduplicated-cond
        -Wduplicated-branches
        -Wlogical-op
        -Wuseless-cast
    )

    if(ENGINE_WARNINGS_AS_ERRORS)
        list(APPEND MSVC_WARNINGS /WX)
        list(APPEND CLANG_WARNINGS -Werror)
        list(APPEND GCC_WARNINGS -Werror)
    endif()

    if(MSVC)
        set(PROJECT_WARNINGS ${MSVC_WARNINGS})
    elseif(CMAKE_CXX_COMPILER_ID MATCHES ".*Clang")
        set(PROJECT_WARNINGS ${CLANG_WARNINGS})
    elseif(CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
        set(PROJECT_WARNINGS ${GCC_WARNINGS})
    else()
        message(WARNING "No warning set defined for compiler '${CMAKE_CXX_COMPILER_ID}'")
    endif()

    target_compile_options(${target} INTERFACE ${PROJECT_WARNINGS})
endfunction()

function(engine_enable_sanitizers target)
    if(NOT ENGINE_ENABLE_SANITIZERS)
        return()
    endif()
    if(MSVC)
        # MSVC supports AddressSanitizer only.
        target_compile_options(${target} INTERFACE /fsanitize=address)
    else()
        set(SANITIZERS address undefined)
        list(JOIN SANITIZERS "," SANITIZER_LIST)
        target_compile_options(${target} INTERFACE -fsanitize=${SANITIZER_LIST} -fno-omit-frame-pointer)
        target_link_options(${target}    INTERFACE -fsanitize=${SANITIZER_LIST})
    endif()
    message(STATUS "Sanitizers enabled on '${target}'")
endfunction()
