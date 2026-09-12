#include "jsengine/debug/debug_session_api.h"

#include <cassert>
#include <iostream>
#include <optional>

int main() {
    jsengine::debug::DebugSessionService service;
    jsengine::debug::DebugSessionApi api(service);

    const auto invalid = api.createSession({.target = ""});
    assert(invalid.status == 400);
    assert(api.createSession({.target = "   "}).status == 400);

    const auto created = api.createSession({.target = "embedded-main"});
    assert(created.status == 201);
    assert(api.createSession({.target = "worker-a"}).status == 201);
    assert(api.createSession({.target = "worker-b"}).status == 201);

    const auto listed = api.listSessions();
    assert(listed.status == 200);
    assert(listed.body.find("embedded-main") != std::string::npos);
    assert(api.listSessions({.limit = 2, .offset = 1}).status == 200);
    assert(api.listSessions({.limit = 0, .offset = 0}).status == 400);

    const auto found = api.getSession("dbg-1");
    assert(found.status == 200);
    assert(api.getSession("").status == 400);

    const auto updated = api.updateSession("dbg-1", {.target = "worker", .paused = true});
    assert(updated.status == 200);
    assert(updated.body.find("worker") != std::string::npos);
    assert(updated.body.find(R"("paused":true)") != std::string::npos);
    assert(api.updateSession("dbg-1", {.target = " ", .paused = std::nullopt}).status == 400);

    const auto deleted = api.deleteSession("dbg-1");
    assert(deleted.status == 204);
    assert(api.deleteSession("").status == 400);

    std::cout << "debug session API tests passed\n";
    return 0;
}
