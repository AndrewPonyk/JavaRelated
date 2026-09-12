# One test executable per module; CTest discovers the individual cases.

set(MINIDB_TEST_TARGETS
  test_lru_replacer
  test_cost_estimator
  test_lexer
  test_tuple_codec
  test_table_heap
  test_bplus_tree
  test_buffer_pool_manager
  test_database
)

foreach(test_name IN LISTS MINIDB_TEST_TARGETS)
  add_executable(${test_name} ${test_name}.cpp)
  target_link_libraries(${test_name} PRIVATE minidb_core GTest::gtest_main)
  target_compile_features(${test_name} PRIVATE cxx_std_20)
  gtest_discover_tests(${test_name})
endforeach()
