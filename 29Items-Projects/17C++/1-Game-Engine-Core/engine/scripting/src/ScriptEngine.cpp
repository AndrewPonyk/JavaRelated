#include "engine/scripting/ScriptEngine.hpp"

#include "engine/core/Log.hpp"
#include "engine/core/ecs/Registry.hpp"
#include "engine/platform/Filesystem.hpp"

#include <sstream>
#include <unordered_map>

#if defined(ENGINE_HAS_LUA)
    #include <sol/sol.hpp>
#endif

/// @file ScriptEngine.cpp
/// @brief Built-in command-registry scripting (always available) + optional Lua.

namespace engine::scripting {
namespace {

std::vector<std::string> tokenize(const std::string& line) {
    std::vector<std::string> tokens;
    std::istringstream       iss(line);
    std::string              tok;
    while (iss >> tok) {
        tokens.push_back(tok);
    }
    return tokens;
}

std::string trim(const std::string& s) {
    const auto begin = s.find_first_not_of(" \t\r\n");
    if (begin == std::string::npos) {
        return {};
    }
    const auto end = s.find_last_not_of(" \t\r\n");
    return s.substr(begin, end - begin + 1);
}

} // namespace

struct ScriptEngine::Impl {
    ecs::Registry*                                   registry = nullptr;
    std::unordered_map<std::string, ScriptCommand>   commands;
#if defined(ENGINE_HAS_LUA)
    sol::state lua;
#endif
};

ScriptEngine::ScriptEngine() = default;
ScriptEngine::~ScriptEngine() {
    shutdown();
}

Result<bool> ScriptEngine::initialize(ecs::Registry& registry, bool sandboxed) {
    sandboxed_      = sandboxed;
    impl_           = std::make_unique<Impl>();
    impl_->registry = &registry;

    // ---- Built-in commands (the always-available scripting surface) ----
    registerCommand("spawn", [this](const std::vector<std::string>&) {
        const ecs::Entity e = impl_->registry->create();
        log::debug("[Script] spawn -> entity {}", e.id);
    });
    registerCommand("log", [](const std::vector<std::string>& args) {
        std::string msg;
        for (usize i = 0; i < args.size(); ++i) {
            msg += args[i];
            if (i + 1 < args.size()) {
                msg += ' ';
            }
        }
        log::info("[Script] {}", msg);
    });

#if defined(ENGINE_HAS_LUA)
    if (sandboxed_) {
        impl_->lua.open_libraries(sol::lib::base, sol::lib::math, sol::lib::table,
                                  sol::lib::string);
    } else {
        impl_->lua.open_libraries();
    }
    // TODO: bind Entity/Registry CRUD into impl_->lua.
    log::info("[Script] Lua + command registry initialized (sandboxed={})", sandboxed_);
#else
    log::info("[Script] command registry initialized ({} built-in commands, sandboxed={})",
              commandCount(), sandboxed_);
#endif
    return ok(true);
}

void ScriptEngine::shutdown() {
    impl_.reset();
}

void ScriptEngine::registerCommand(std::string name, ScriptCommand handler) {
    if (!impl_) {
        return;
    }
    impl_->commands[std::move(name)] = std::move(handler);
}

bool ScriptEngine::hasCommand(const std::string& name) const {
    return impl_ && impl_->commands.find(name) != impl_->commands.end();
}

usize ScriptEngine::commandCount() const {
    return impl_ ? impl_->commands.size() : 0;
}

Result<bool> ScriptEngine::execute(const std::string& commandLine) {
    if (!impl_) {
        return err<bool>(ErrorCode::Unknown, "script engine not initialized");
    }
    const auto tokens = tokenize(trim(commandLine));
    if (tokens.empty()) {
        return ok(true); // empty line is a no-op
    }
    const auto it = impl_->commands.find(tokens[0]);
    if (it == impl_->commands.end()) {
        return err<bool>(ErrorCode::NotFound, "unknown command: " + tokens[0]);
    }
    const std::vector<std::string> args(tokens.begin() + 1, tokens.end());
    it->second(args);
    return ok(true);
}

Result<bool> ScriptEngine::runString(const std::string& source) {
    if (!impl_) {
        return err<bool>(ErrorCode::Unknown, "script engine not initialized");
    }
#if defined(ENGINE_HAS_LUA)
    const sol::protected_function_result r =
        impl_->lua.safe_script(source, sol::script_pass_on_error);
    if (!r.valid()) {
        const sol::error e = r;
        return err<bool>(ErrorCode::DeserializeError, std::string("lua error: ") + e.what());
    }
    return ok(true);
#else
    bool        allOk = true;
    std::string firstError;
    std::istringstream iss(source);
    std::string        line;
    while (std::getline(iss, line)) {
        const std::string t = trim(line);
        if (t.empty() || t[0] == '#') {
            continue; // blank line or comment
        }
        if (auto r = execute(t); !r) {
            allOk = false;
            if (firstError.empty()) {
                firstError = r.error().message;
            }
            log::warn("[Script] {}", r.error().message);
        }
    }
    return allOk ? Result<bool>(ok(true))
                 : err<bool>(ErrorCode::DeserializeError, firstError);
#endif
}

Result<bool> ScriptEngine::runFile(const std::string& path) {
    auto text = platform::fs::readText(platform::fs::resolveAsset(path));
    if (!text) {
        return text.error();
    }
    return runString(text.value());
}

void ScriptEngine::reloadIfChanged() {
    // TODO: track watched files' mtimes and re-run changed ones (engine supports
    // hot-reload; the file-watch wiring is left to the editor integration).
}

void ScriptEngine::callOnUpdate(f32 dt) {
    if (!impl_) {
        return;
    }
#if defined(ENGINE_HAS_LUA)
    sol::protected_function hook = impl_->lua["on_update"];
    if (hook.valid()) {
        const sol::protected_function_result r = hook(dt);
        if (!r.valid()) {
            const sol::error e = r;
            log::warn("[Script] on_update error: {}", e.what());
        }
    }
#else
    if (const auto it = impl_->commands.find("on_update"); it != impl_->commands.end()) {
        it->second({std::to_string(dt)});
    }
#endif
}

} // namespace engine::scripting
