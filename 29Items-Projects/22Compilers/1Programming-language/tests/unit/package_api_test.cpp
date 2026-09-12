#include "plang/api/PackageApi.h"
#include "plang/pkg/PackageManager.h"

#include <cassert>
#include <string>

int main() {
    const std::string coreSha =
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    const std::string utilSha =
        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    plang::pkg::PackageManager manager;
    plang::api::PackageApi api(manager);

    auto create = api.handle({"POST", "/packages", "name=core", {}});
    assert(create.status == 201);

    auto publish = api.handle({"POST",
                               "/packages/core/versions",
                               "version=1.2.0&sha256=" + coreSha + "&deps=util@>=1.0.0",
                               {}});
    assert(publish.status == 201);

    auto util = api.handle({"POST",
                            "/packages/util/versions",
                            "version=1.0.1&sha256=" + utilSha + "&manifest={}",
                            {}});
    assert(util.status == 201);

    auto list = api.handle({"GET", "/packages?limit=1&offset=0", "", {}});
    assert(list.status == 200);
    assert(list.body.find("\"limit\":1") != std::string::npos);
    assert(list.body.find("\"core\"") != std::string::npos);

    auto version = api.handle({"GET", "/packages/core/versions/1.2.0", "", {}});
    assert(version.status == 200);
    assert(version.body.find(coreSha) != std::string::npos);

    auto resolved = api.handle({"POST", "/resolve", "deps=core@^1.0.0", {}});
    assert(resolved.status == 200);
    assert(resolved.body.find("1.2.0") != std::string::npos);
    assert(resolved.body.find("1.0.1") != std::string::npos);

    auto yanked = api.handle({"POST", "/packages/core/versions/1.2.0/yank", "", {}});
    assert(yanked.status == 200);

    auto deleted = api.handle({"DELETE", "/packages/core/versions/1.2.0", "", {}});
    assert(deleted.status == 200);

    auto badChecksum =
        api.handle({"POST", "/packages/core/versions", "version=2.0.0&sha256=abc", {}});
    assert(badChecksum.status == 400);

    auto badDependency = api.handle({"POST", "/resolve", "deps=bad/name@^1.0.0", {}});
    assert(badDependency.status == 400);

    auto missing = api.handle({"GET", "/packages/missing", "", {}});
    assert(missing.status == 404);
    return 0;
}
