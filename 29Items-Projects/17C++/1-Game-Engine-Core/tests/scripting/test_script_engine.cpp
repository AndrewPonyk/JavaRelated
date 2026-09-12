#include "engine/core/ecs/Registry.hpp"
#include "engine/scripting/ScriptEngine.hpp"

#include <gtest/gtest.h>

#include <string>
#include <vector>

using namespace engine;
using namespace engine::scripting;

TEST(ScriptEngine, RegistersBuiltinCommands) {
    ecs::Registry r;
    ScriptEngine  se;
    ASSERT_TRUE(se.initialize(r));
    EXPECT_TRUE(se.hasCommand("spawn"));
    EXPECT_TRUE(se.hasCommand("log"));
    EXPECT_GE(se.commandCount(), 2u);
}

TEST(ScriptEngine, SpawnCommandCreatesEntity) {
    ecs::Registry r;
    ScriptEngine  se;
    se.initialize(r);
    ASSERT_TRUE(se.execute("spawn"));
    EXPECT_EQ(r.aliveCount(), 1u);
}

TEST(ScriptEngine, CustomCommandReceivesArgs) {
    ecs::Registry r;
    ScriptEngine  se;
    se.initialize(r);
    int argCount = 0;
    se.registerCommand("ping",
                       [&argCount](const std::vector<std::string>& args) {
                           argCount = static_cast<int>(args.size());
                       });
    ASSERT_TRUE(se.execute("ping a b c"));
    EXPECT_EQ(argCount, 3);
}

TEST(ScriptEngine, UnknownCommandReturnsError) {
    ecs::Registry r;
    ScriptEngine  se;
    se.initialize(r);
    EXPECT_FALSE(se.execute("does_not_exist"));
}

TEST(ScriptEngine, RunStringExecutesEachLine) {
    ecs::Registry r;
    ScriptEngine  se;
    se.initialize(r);
    ASSERT_TRUE(se.runString("spawn\nspawn\n# a comment\n\nspawn"));
    EXPECT_EQ(r.aliveCount(), 3u);
}
