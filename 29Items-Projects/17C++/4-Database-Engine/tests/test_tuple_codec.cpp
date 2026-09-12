// Unit tests for schema-driven tuple (de)serialization.

#include <gtest/gtest.h>

#include <cstdint>
#include <string>
#include <vector>

#include "minidb/catalog/schema.hpp"
#include "minidb/catalog/tuple_codec.hpp"

using minidb::Schema;
using minidb::TupleCodec;
using minidb::TypeId;
using minidb::Value;

namespace {
Schema MakeSchema() {
  return Schema({{"a", TypeId::kInteger, 0},
                 {"b", TypeId::kVarchar, 32},
                 {"c", TypeId::kBigInt, 0},
                 {"d", TypeId::kBoolean, 0}});
}
}  // namespace

TEST(TupleCodec, RoundTripsAllColumnTypes) {
  const Schema schema = MakeSchema();
  const std::vector<Value> row = {Value(std::int32_t{-7}),
                                  Value(std::string("hello")),
                                  Value(std::int64_t{1} << 40), Value(true)};

  const std::string bytes = TupleCodec::Serialize(schema, row);
  const auto back = TupleCodec::Deserialize(schema, bytes.data(), bytes.size());

  ASSERT_EQ(back.size(), 4u);
  EXPECT_EQ(back[0].AsInt64().value(), -7);
  EXPECT_EQ(back[1].AsString().value(), "hello");
  EXPECT_EQ(back[2].AsInt64().value(), (std::int64_t{1} << 40));
  EXPECT_EQ(back[3].AsInt64().value(), 1);
}

TEST(TupleCodec, PreservesNulls) {
  const Schema schema = MakeSchema();
  const std::vector<Value> row = {Value(), Value(std::string("x")), Value(),
                                  Value()};

  const std::string bytes = TupleCodec::Serialize(schema, row);
  const auto back = TupleCodec::Deserialize(schema, bytes.data(), bytes.size());

  EXPECT_TRUE(back[0].is_null());
  EXPECT_FALSE(back[1].is_null());
  EXPECT_EQ(back[1].AsString().value(), "x");
  EXPECT_TRUE(back[2].is_null());
  EXPECT_TRUE(back[3].is_null());
}

TEST(TupleCodec, HandlesEmptyVarchar) {
  const Schema schema({{"s", TypeId::kVarchar, 16}});
  const std::vector<Value> row = {Value(std::string())};

  const std::string bytes = TupleCodec::Serialize(schema, row);
  const auto back = TupleCodec::Deserialize(schema, bytes.data(), bytes.size());

  ASSERT_EQ(back.size(), 1u);
  EXPECT_FALSE(back[0].is_null());
  EXPECT_EQ(back[0].AsString().value(), "");
}

TEST(TupleCodec, ShortBufferDecodesDefensively) {
  const Schema schema = MakeSchema();
  // A truncated buffer must not read out of bounds; missing fields become NULL.
  const auto back = TupleCodec::Deserialize(schema, "", 0);
  EXPECT_EQ(back.size(), 4u);
}
