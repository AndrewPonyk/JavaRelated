# Registers each test_*.c as its own CTest case, linked against ppmon_core.

set(PPMON_TESTS
    test_alerting
    test_csv_export
    test_config
    test_sample_store
)

foreach(test_name IN LISTS PPMON_TESTS)
    add_executable(${test_name} ${test_name}.c)
    target_link_libraries(${test_name} PRIVATE ppmon_core)
    target_include_directories(${test_name} PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}")
    ppmon_set_warnings(${test_name})
    add_test(NAME ${test_name} COMMAND ${test_name})
endforeach()

# Live-OS integration tests are opt-in (kept out of the deterministic gate
# because they touch the real kernel/PDH subsystems).
if(DEFINED ENV{PPMON_RUN_INTEGRATION} AND "$ENV{PPMON_RUN_INTEGRATION}" STREQUAL "1")
    message(STATUS "ppmon: integration tests enabled")
    add_executable(test_integration test_integration.c)
    target_link_libraries(test_integration PRIVATE ppmon_core)
    target_include_directories(test_integration PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}")
    ppmon_set_warnings(test_integration)
    add_test(NAME test_integration COMMAND test_integration)
endif()
