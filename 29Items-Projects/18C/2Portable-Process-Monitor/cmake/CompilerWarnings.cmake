# CompilerWarnings.cmake — centralised, opt-in strict warnings.
# Call ppmon_set_warnings(<target>) on each target.

function(ppmon_set_warnings target)
    if(MSVC)
        target_compile_options(${target} PRIVATE
            /W4         # high warning level
            /WX         # warnings are errors
            /permissive- # stricter standard conformance
            /wd4127     # conditional expression is constant (intentional in loops)
        )
        # The CRT "secure function" deprecations (C4996) flag standard, correctly
        # bounded calls (strncpy/fopen/strcpy). We rely on portable C, not the
        # _s variants, so silence them rather than fork the code per-compiler.
        target_compile_definitions(${target} PRIVATE _CRT_SECURE_NO_WARNINGS)
        # /analyze (static analysis) is enabled in CI only to keep local builds fast.
        if(DEFINED ENV{PPMON_ENABLE_ANALYZE})
            target_compile_options(${target} PRIVATE /analyze)
        endif()
    else()
        # GCC/Clang path (for portable-core unit testing off Windows).
        target_compile_options(${target} PRIVATE
            -Wall -Wextra -Wpedantic -Werror
            -Wshadow -Wconversion -Wsign-conversion
        )
    endif()
endfunction()
