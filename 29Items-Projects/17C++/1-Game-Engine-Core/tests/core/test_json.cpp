#include "engine/core/Json.hpp"

#include <gtest/gtest.h>

#include <string>

using engine::Json;

TEST(Json, ParsesPrimitives) {
    auto b = Json::parse("true");
    ASSERT_TRUE(b);
    EXPECT_TRUE(b.value().asBool());

    auto n = Json::parse("42");
    ASSERT_TRUE(n);
    EXPECT_EQ(n.value().asInt(), 42);

    auto f = Json::parse("-3.5");
    ASSERT_TRUE(f);
    EXPECT_FLOAT_EQ(f.value().asFloat(), -3.5f);

    auto s = Json::parse("\"hello\"");
    ASSERT_TRUE(s);
    EXPECT_EQ(s.value().asString(), "hello");

    auto z = Json::parse("null");
    ASSERT_TRUE(z);
    EXPECT_TRUE(z.value().isNull());
}

TEST(Json, ParsesNestedObjectsAndArrays) {
    auto r = Json::parse(R"({"a":1,"b":[10,20,30],"c":{"d":true}})");
    ASSERT_TRUE(r);
    const Json& j = r.value();
    EXPECT_TRUE(j.isObject());
    EXPECT_EQ(j.at("a").asInt(), 1);
    ASSERT_EQ(j.at("b").size(), 3u);
    EXPECT_EQ(j.at("b")[1].asInt(), 20);
    EXPECT_TRUE(j.at("c").at("d").asBool());
}

TEST(Json, RoundTripsThroughDump) {
    Json o   = Json::object();
    o["name"]  = "engine";
    o["count"] = 3;
    o["flag"]  = true;
    Json list  = Json::array();
    list.push_back(1);
    list.push_back(2);
    o["list"] = list;

    auto re = Json::parse(o.dump());
    ASSERT_TRUE(re);
    EXPECT_EQ(re.value().at("name").asString(), "engine");
    EXPECT_EQ(re.value().at("count").asInt(), 3);
    EXPECT_EQ(re.value().at("list").size(), 2u);
}

TEST(Json, PreservesStringEscapes) {
    Json s   = std::string("line1\ntab\there\"quote\"");
    auto re  = Json::parse(s.dump());
    ASSERT_TRUE(re);
    EXPECT_EQ(re.value().asString(), "line1\ntab\there\"quote\"");
}

TEST(Json, RejectsMalformedInput) {
    EXPECT_FALSE(Json::parse("{bad}"));
    EXPECT_FALSE(Json::parse("[1,2"));
    EXPECT_FALSE(Json::parse(""));
    EXPECT_FALSE(Json::parse("{\"k\":}"));
}

TEST(Json, RejectsExcessivelyNestedInput) {
    // Hostile deeply-nested input must fail safely (bounded recursion), not crash.
    std::string deep(2000, '[');
    EXPECT_FALSE(Json::parse(deep));
}

TEST(Json, MissingKeysReturnNull) {
    auto r = Json::parse(R"({"x":1})");
    ASSERT_TRUE(r);
    EXPECT_TRUE(r.value().at("missing").isNull());
    EXPECT_EQ(r.value().at("missing").asInt(99), 99); // default fallback
}
