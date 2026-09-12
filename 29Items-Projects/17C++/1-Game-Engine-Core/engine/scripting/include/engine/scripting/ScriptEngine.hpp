#pragma once

#include "engine/core/Result.hpp"
#include "engine/core/Types.hpp"

#include <functional>
#include <memory>
#include <string>
#include <vector>

/// @file ScriptEngine.hpp
/// @brief Gameplay scripting with two layers:
///   1. A built-in **command registry** — always available, zero dependencies.
///      Scripts are newline-separated `command arg1 arg2 ...` lines dispatched to
///      registered C++ handlers (sandbox-safe by construction).
///   2. An optional **Lua** backend (sol2) for full scripting when available.
///
/// In shipped builds the Lua standard library is restricted and the command surface
/// is curated, so mods cannot reach the filesystem/process (see docs/ARCHITECTURE.md §2.5).

namespace engine::ecs {
class Registry;
}

namespace engine::scripting {

/// A registered command: receives the parsed argument list.
using ScriptCommand = std::function<void(const std::vector<std::string>& args)>;

class ScriptEngine {
public:
    ScriptEngine();   // defined in .cpp (pimpl)
    ~ScriptEngine();

    ScriptEngine(const ScriptEngine&)            = delete;
    ScriptEngine& operator=(const ScriptEngine&) = delete;

    /// Create the engine + install built-in commands (and Lua, if compiled in).
    [[nodiscard]] Result<bool> initialize(ecs::Registry& registry, bool sandboxed = true);
    void                       shutdown();

    /// Register a command handler. Overwrites any existing command of the same name.
    void registerCommand(std::string name, ScriptCommand handler);
    [[nodiscard]] bool hasCommand(const std::string& name) const;
    [[nodiscard]] usize commandCount() const;

    /// Execute a single `command arg...` line via the command registry.
    [[nodiscard]] Result<bool> execute(const std::string& commandLine);

    /// Run a multi-line script: each non-empty, non-`#`-comment line is a command.
    /// If the Lua backend is compiled in, the source is run as Lua instead.
    [[nodiscard]] Result<bool> runString(const std::string& source);

    /// Load + run a script file (Lua when available, else command lines).
    [[nodiscard]] Result<bool> runFile(const std::string& path);

    /// Re-run watched scripts whose file changed (hot-reload).
    void reloadIfChanged();

    /// Invoke an optional `on_update(dt)` hook (Lua) / "on_update" command.
    void callOnUpdate(f32 dt);

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
    bool                  sandboxed_ = true;
};

} // namespace engine::scripting
