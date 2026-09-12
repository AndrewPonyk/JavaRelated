# third_party/sqlite/CMakeLists.txt
# Builds the vendored SQLite amalgamation as a static library. Strict project
# warnings are intentionally NOT applied here (it is third-party C).

add_library(sqlite3 STATIC sqlite3.c)

target_include_directories(sqlite3 PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

# Trim features we don't need; keeps the build fast and the binary small.
target_compile_definitions(sqlite3 PRIVATE
    SQLITE_THREADSAFE=1
    SQLITE_OMIT_LOAD_EXTENSION
    SQLITE_DQS=0
    SQLITE_DEFAULT_FOREIGN_KEYS=1)

if(NOT MSVC)
    target_compile_options(sqlite3 PRIVATE -w)            # silence 3rd-party warnings
    find_package(Threads REQUIRED)
    target_link_libraries(sqlite3 PUBLIC Threads::Threads ${CMAKE_DL_LIBS})
else()
    target_compile_options(sqlite3 PRIVATE /w)
endif()

set_target_properties(sqlite3 PROPERTIES POSITION_INDEPENDENT_CODE ON)
