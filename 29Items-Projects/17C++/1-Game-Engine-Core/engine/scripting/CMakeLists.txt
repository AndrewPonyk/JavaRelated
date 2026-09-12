# engine/scripting — Lua (sol2) gameplay scripting with hot-reload + sandbox.

add_library(engine_scripting src/ScriptEngine.cpp)
add_library(engine::scripting ALIAS engine_scripting)

target_include_directories(engine_scripting
    PUBLIC $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
)
target_link_libraries(engine_scripting
    PUBLIC  engine::core
    PRIVATE engine_project_options engine::platform
)

if(TARGET sol2::sol2 AND LUA_FOUND)
    target_link_libraries(engine_scripting PRIVATE sol2::sol2 ${LUA_LIBRARIES})
    target_include_directories(engine_scripting PRIVATE ${LUA_INCLUDE_DIR})
    target_compile_definitions(engine_scripting PRIVATE ENGINE_HAS_LUA=1)
else()
    message(WARNING "[scripting] Lua/sol2 not found — scripting disabled.")
endif()
