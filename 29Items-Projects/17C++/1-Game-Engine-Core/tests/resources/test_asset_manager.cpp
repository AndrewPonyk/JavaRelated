#include "engine/core/jobs/JobSystem.hpp"
#include "engine/resources/AssetManager.hpp"

#include <gtest/gtest.h>

#include <filesystem>
#include <fstream>

using namespace engine;
using namespace engine::resources;

TEST(AssetManager, LoadsExistingFileAndReportsSize) {
    namespace fs   = std::filesystem;
    const auto path = fs::temp_directory_path() / "gec_asset_test.bin";
    {
        std::ofstream f(path, std::ios::binary);
        const char    data[8] = {1, 2, 3, 4, 5, 6, 7, 8};
        f.write(data, sizeof(data));
    }

    jobs::JobSystem js;
    js.start(2);
    AssetManager am(js);

    const AssetHandle h = am.load(path.string(), AssetType::Mesh);
    am.waitAll();
    EXPECT_EQ(am.loadState(h), LoadState::Ready);
    EXPECT_EQ(am.loadedSize(h), 8u);

    js.stop();
    fs::remove(path);
}

TEST(AssetManager, FailsOnMissingFile) {
    jobs::JobSystem js;
    js.start(2);
    AssetManager am(js);

    const AssetHandle h = am.load("definitely/missing/zzz_asset.bin", AssetType::Mesh);
    am.waitAll();
    EXPECT_EQ(am.loadState(h), LoadState::Failed);

    js.stop();
}

TEST(AssetManager, DeduplicatesByPath) {
    jobs::JobSystem js; // not started -> loads run synchronously
    AssetManager    am(js);

    const AssetHandle h1 = am.load("same/path.bin", AssetType::Texture);
    const AssetHandle h2 = am.load("same/path.bin", AssetType::Texture);
    EXPECT_EQ(h1.id, h2.id); // second request reuses the same entry
}
