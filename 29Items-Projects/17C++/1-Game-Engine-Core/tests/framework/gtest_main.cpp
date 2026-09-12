// Entry point for the GoogleTest-compatible shim (used when real GTest is absent).
#include <gtest/gtest.h>

int main() {
    return RUN_ALL_TESTS();
}
