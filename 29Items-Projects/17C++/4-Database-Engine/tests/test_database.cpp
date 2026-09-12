// End-to-end integration tests driving the public Database façade through
// lex -> parse -> plan -> execute against real disk-backed storage.

#include <gtest/gtest.h>

#include <algorithm>
#include <cstdint>
#include <filesystem>
#include <string>
#include <vector>

#include "minidb/engine/database.hpp"

using minidb::Database;
using minidb::StatusCode;

namespace {
class DatabaseTest : public ::testing::Test {
 protected:
  void SetUp() override {
    path_ = (std::filesystem::temp_directory_path() /
             ("minidb_db_" +
              std::to_string(reinterpret_cast<std::uintptr_t>(this)) + ".db"))
                .string();
    std::error_code ec;
    std::filesystem::remove(path_, ec);
  }
  void TearDown() override {
    std::error_code ec;
    std::filesystem::remove(path_, ec);
  }
  std::string path_;
};
}  // namespace

TEST_F(DatabaseTest, CreateInsertSelectPointQuery) {
  auto opened = Database::Open(path_);
  ASSERT_TRUE(opened.ok()) << opened.status().ToString();
  Database& db = opened.value();

  ASSERT_TRUE(db.Execute("CREATE TABLE users (id INT, name VARCHAR(32))").ok());
  ASSERT_TRUE(
      db.Execute("INSERT INTO users VALUES (1,'Ada'),(2,'Linus'),(3,'Grace')")
          .ok());

  auto result = db.Execute("SELECT id, name FROM users WHERE id = 2");
  ASSERT_TRUE(result.ok()) << result.status().ToString();
  const auto& rs = result.value();
  ASSERT_EQ(rs.columns.size(), 2u);
  ASSERT_EQ(rs.rows.size(), 1u);
  EXPECT_EQ(rs.rows[0][0].ToString(), "2");
  EXPECT_EQ(rs.rows[0][1].ToString(), "Linus");
}

TEST_F(DatabaseTest, RangeQueryReturnsTheRightSet) {
  auto opened = Database::Open(path_);
  ASSERT_TRUE(opened.ok());
  Database& db = opened.value();

  ASSERT_TRUE(db.Execute("CREATE TABLE t (k INT, v VARCHAR(8))").ok());
  ASSERT_TRUE(
      db.Execute("INSERT INTO t VALUES (5,'e'),(1,'a'),(3,'c'),(2,'b'),(4,'d')")
          .ok());

  auto result = db.Execute("SELECT k FROM t WHERE k >= 2");
  ASSERT_TRUE(result.ok()) << result.status().ToString();

  std::vector<std::string> got;
  for (const auto& row : result.value().rows) got.push_back(row[0].ToString());
  std::sort(got.begin(), got.end());
  EXPECT_EQ(got, (std::vector<std::string>{"2", "3", "4", "5"}));
}

TEST_F(DatabaseTest, SelectStarProjectsAllColumns) {
  auto opened = Database::Open(path_);
  ASSERT_TRUE(opened.ok());
  Database& db = opened.value();

  ASSERT_TRUE(db.Execute("CREATE TABLE t (a INT, b INT, c VARCHAR(4))").ok());
  ASSERT_TRUE(db.Execute("INSERT INTO t VALUES (1, 2, 'x')").ok());

  auto result = db.Execute("SELECT * FROM t");
  ASSERT_TRUE(result.ok());
  EXPECT_EQ(result.value().columns.size(), 3u);
  ASSERT_EQ(result.value().rows.size(), 1u);
  EXPECT_EQ(result.value().rows[0].size(), 3u);
}

TEST_F(DatabaseTest, DuplicatePrimaryKeyRejected) {
  auto opened = Database::Open(path_);
  ASSERT_TRUE(opened.ok());
  Database& db = opened.value();

  ASSERT_TRUE(db.Execute("CREATE TABLE t (id INT, n VARCHAR(4))").ok());
  ASSERT_TRUE(db.Execute("INSERT INTO t VALUES (1,'a')").ok());

  auto result = db.Execute("INSERT INTO t VALUES (1,'b')");
  EXPECT_FALSE(result.ok());
  EXPECT_EQ(result.status().code(), StatusCode::kAlreadyExists);
}

TEST_F(DatabaseTest, SyntaxErrorIsSurfaced) {
  auto opened = Database::Open(path_);
  ASSERT_TRUE(opened.ok());
  Database& db = opened.value();

  auto result = db.Execute("SELCT oops");
  EXPECT_FALSE(result.ok());
  EXPECT_EQ(result.status().code(), StatusCode::kSyntaxError);
}

TEST_F(DatabaseTest, UnknownTableIsSurfaced) {
  auto opened = Database::Open(path_);
  ASSERT_TRUE(opened.ok());
  Database& db = opened.value();

  auto result = db.Execute("SELECT * FROM does_not_exist");
  EXPECT_FALSE(result.ok());
  EXPECT_EQ(result.status().code(), StatusCode::kNotFound);
}

TEST_F(DatabaseTest, DataAndSchemaPersistAcrossReopen) {
  {  // session 1: create + insert + flush, then close
    auto opened = Database::Open(path_);
    ASSERT_TRUE(opened.ok()) << opened.status().ToString();
    Database& db = opened.value();
    ASSERT_TRUE(db.Execute("CREATE TABLE t (id INT, n VARCHAR(8))").ok());
    ASSERT_TRUE(
        db.Execute("INSERT INTO t VALUES (1,'a'),(2,'b'),(3,'c')").ok());
    ASSERT_TRUE(db.Flush().ok());
  }
  {  // session 2: a fresh open of the same file must see the data + schema
    auto opened = Database::Open(path_);
    ASSERT_TRUE(opened.ok()) << opened.status().ToString();
    Database& db = opened.value();

    EXPECT_EQ(db.catalog().TableNames().size(), 1u);
    auto result = db.Execute("SELECT id, n FROM t WHERE id = 2");
    ASSERT_TRUE(result.ok()) << result.status().ToString();
    ASSERT_EQ(result.value().rows.size(), 1u);
    EXPECT_EQ(result.value().rows[0][1].ToString(), "b");
  }
}

TEST_F(DatabaseTest, ExplainReturnsAPlan) {
  auto opened = Database::Open(path_);
  ASSERT_TRUE(opened.ok());
  Database& db = opened.value();

  ASSERT_TRUE(db.Execute("CREATE TABLE t (id INT, n VARCHAR(8))").ok());
  ASSERT_TRUE(db.Execute("INSERT INTO t VALUES (1,'a')").ok());

  auto result = db.Execute("EXPLAIN SELECT n FROM t WHERE id = 1");
  ASSERT_TRUE(result.ok()) << result.status().ToString();
  ASSERT_EQ(result.value().columns.size(), 1u);
  EXPECT_EQ(result.value().columns[0], "query plan");
  EXPECT_GE(result.value().rows.size(), 1u);
}

TEST_F(DatabaseTest, RejectsValueTooLongForVarchar) {
  auto opened = Database::Open(path_);
  ASSERT_TRUE(opened.ok());
  Database& db = opened.value();

  ASSERT_TRUE(db.Execute("CREATE TABLE t (id INT, n VARCHAR(4))").ok());
  auto result = db.Execute("INSERT INTO t VALUES (1, 'toolong')");
  EXPECT_FALSE(result.ok());
  EXPECT_EQ(result.status().code(), StatusCode::kInvalidArgument);
}

TEST_F(DatabaseTest, RejectsIntegerOutOfRange) {
  auto opened = Database::Open(path_);
  ASSERT_TRUE(opened.ok());
  Database& db = opened.value();

  ASSERT_TRUE(db.Execute("CREATE TABLE t (id INT, n VARCHAR(8))").ok());
  // 9999999999 overflows a 32-bit INT column.
  auto result = db.Execute("INSERT INTO t VALUES (9999999999, 'x')");
  EXPECT_FALSE(result.ok());
  EXPECT_EQ(result.status().code(), StatusCode::kInvalidArgument);
}

TEST_F(DatabaseTest, LargeTablePrefersIndexScanForPointQuery) {
  auto opened = Database::Open(path_);
  ASSERT_TRUE(opened.ok());
  Database& db = opened.value();

  ASSERT_TRUE(db.Execute("CREATE TABLE big (id INT, blob VARCHAR(200))").ok());
  const std::string blob(200, 'x');
  for (int i = 0; i < 1500; ++i) {
    const std::string sql =
        "INSERT INTO big VALUES (" + std::to_string(i) + ", '" + blob + "')";
    ASSERT_TRUE(db.Execute(sql).ok());
  }

  auto result = db.Execute("SELECT id FROM big WHERE id = 750");
  ASSERT_TRUE(result.ok()) << result.status().ToString();
  ASSERT_EQ(result.value().rows.size(), 1u);
  EXPECT_EQ(result.value().rows[0][0].ToString(), "750");
  // With wide rows and a selective predicate, the planner chooses the index.
  EXPECT_NE(result.value().message.find("IndexScan"), std::string::npos);
}
