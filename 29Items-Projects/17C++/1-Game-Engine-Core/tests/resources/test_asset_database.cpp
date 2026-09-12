#include "engine/resources/AssetDatabase.hpp"

#include <gtest/gtest.h>

#include <cstdio>
#include <string>

using namespace engine::resources;

TEST(AssetDatabase, UpsertAndFind) {
    AssetDatabase db;
    ASSERT_TRUE(db.open("test_assetdb_find.json"));

    AssetRecord rec;
    rec.guid        = "g1";
    rec.sourcePath  = "meshes/a.mesh";
    rec.contentHash = "abc123";
    rec.type        = 1;
    ASSERT_TRUE(db.upsertAsset(rec));

    auto byGuid = db.findByGuid("g1");
    ASSERT_TRUE(byGuid.has_value());
    EXPECT_EQ(byGuid->sourcePath, "meshes/a.mesh");

    auto byPath = db.findBySourcePath("meshes/a.mesh");
    ASSERT_TRUE(byPath.has_value());
    EXPECT_EQ(byPath->guid, "g1");

    EXPECT_FALSE(db.findByGuid("missing").has_value());

    db.close();
    std::remove("test_assetdb_find.json");
}

TEST(AssetDatabase, PersistsAcrossReopen) {
    const std::string path = "test_assetdb_persist.json";
    {
        AssetDatabase db;
        db.open(path);
        AssetRecord r;
        r.guid        = "x";
        r.sourcePath  = "p";
        r.contentHash = "h";
        r.type        = 2;
        db.upsertAsset(r);
        db.close();
    }
    {
        AssetDatabase db;
        db.open(path);
        auto f = db.findByGuid("x");
        ASSERT_TRUE(f.has_value());
        EXPECT_EQ(f->type, 2u);
        db.close();
    }
    std::remove(path.c_str());
}

TEST(AssetDatabase, TerrainTileCache) {
    const std::string path = "test_assetdb_tiles.json";
    AssetDatabase     db;
    db.open(path);

    EXPECT_FALSE(db.findTile(1, 0, 0, "v1").has_value());

    TerrainTileRecord t;
    t.seed         = 1;
    t.tileX        = 0;
    t.tileY        = 0;
    t.biome        = "grass";
    t.modelVersion = "v1";
    t.meshBlobPath = "blob";
    ASSERT_TRUE(db.cacheTile(t));

    auto f = db.findTile(1, 0, 0, "v1");
    ASSERT_TRUE(f.has_value());
    EXPECT_EQ(f->biome, "grass");

    db.close();
    std::remove(path.c_str());
}

TEST(AssetDatabase, RejectsEmptyGuid) {
    AssetDatabase db;
    db.open("test_assetdb_reject.json");
    AssetRecord r; // guid empty
    EXPECT_FALSE(db.upsertAsset(r));
    db.close();
    std::remove("test_assetdb_reject.json");
}
