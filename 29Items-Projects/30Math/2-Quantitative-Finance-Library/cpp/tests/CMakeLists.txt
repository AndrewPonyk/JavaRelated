include(FetchContent)
FetchContent_Declare(
    Catch2
    GIT_REPOSITORY https://github.com/catchorg/Catch2.git
    GIT_TAG v3.5.4
)
FetchContent_MakeAvailable(Catch2)

add_executable(qfcore_tests
    test_black_scholes.cpp
)
target_link_libraries(qfcore_tests PRIVATE qfcore Catch2::Catch2WithMain)

include(CTest)
include(Catch)
catch_discover_tests(qfcore_tests)
