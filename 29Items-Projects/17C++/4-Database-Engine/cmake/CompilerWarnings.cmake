# Reusable helper that applies a sensible warning set to a target.
# Warnings-as-errors is opt-in via -DMINIDB_WARNINGS_AS_ERRORS=ON (CI turns it on).

function(minidb_set_warnings target)
  if(MSVC)
    target_compile_options(${target} PRIVATE /W4 /permissive- /EHsc)
    # std::getenv et al. are standard C++; silence MSVC's non-portable
    # "consider the _s variant" deprecation so /WX builds stay clean.
    target_compile_definitions(${target} PRIVATE _CRT_SECURE_NO_WARNINGS)
    if(MINIDB_WARNINGS_AS_ERRORS)
      target_compile_options(${target} PRIVATE /WX)
    endif()
  else()
    target_compile_options(${target} PRIVATE
      -Wall -Wextra -Wpedantic -Wshadow -Wnon-virtual-dtor -Wold-style-cast
      -Wcast-align -Wunused -Woverloaded-virtual -Wnull-dereference
      -Wdouble-promotion)
    if(MINIDB_WARNINGS_AS_ERRORS)
      target_compile_options(${target} PRIVATE -Werror)
    endif()
  endif()
endfunction()
