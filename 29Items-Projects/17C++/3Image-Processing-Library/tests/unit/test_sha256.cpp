// tests/unit/test_sha256.cpp
#include "imgproc/util/sha256.hpp"

#include <gtest/gtest.h>

namespace {

using imgproc::util::Sha256;

TEST(Sha256Test, EmptyString) {
    EXPECT_EQ(Sha256::hashString(""),
              "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
}

TEST(Sha256Test, Abc) {
    EXPECT_EQ(Sha256::hashString("abc"),
              "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
}

TEST(Sha256Test, QuickBrownFox) {
    EXPECT_EQ(Sha256::hashString("The quick brown fox jumps over the lazy dog"),
              "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592");
}

TEST(Sha256Test, StreamingMatchesOneShot) {
    Sha256 h;
    h.update("The quick brown ", 16);
    h.update("fox jumps over the lazy dog", 27);
    EXPECT_EQ(h.hexDigest(),
              "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592");
}

}  // namespace
