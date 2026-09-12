# qfcore: static numerics library + optional pybind11 module + optional tests.

add_library(qfcore STATIC
    src/black_scholes.cpp
    src/monte_carlo.cpp
    src/american.cpp
    src/var.cpp
)
target_include_directories(qfcore PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)

# OpenMP is optional: used by the Monte Carlo engine when available.
find_package(OpenMP QUIET)
if(OpenMP_CXX_FOUND)
    target_link_libraries(qfcore PRIVATE OpenMP::OpenMP_CXX)
    target_compile_definitions(qfcore PRIVATE QFCORE_HAS_OPENMP)
endif()

if(QFCORE_BUILD_BINDINGS)
    find_package(pybind11 CONFIG REQUIRED)
    pybind11_add_module(_qfcore bindings/bindings.cpp)
    target_link_libraries(_qfcore PRIVATE qfcore)
    target_compile_definitions(_qfcore PRIVATE QFCORE_VERSION="${PROJECT_VERSION}")
    # scikit-build-core installs this next to the Python package sources.
    install(TARGETS _qfcore DESTINATION quantfinlib)
endif()

if(QFCORE_BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
endif()
