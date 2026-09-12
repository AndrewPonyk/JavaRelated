// ============================================================================
//  tests/shim/gtest/gtest.h
//  A tiny, dependency-free GoogleTest-compatible shim implementing the subset
//  of the GTest API this project uses. CMake prefers the real GTest when it is
//  installed; otherwise it falls back to this shim so `ctest` works everywhere
//  with only a C++20 compiler. The test sources are identical either way.
// ============================================================================
#pragma once

#include <cmath>
#include <cstdio>
#include <functional>
#include <string>
#include <vector>

namespace rts_test {

struct TestCase {
    std::string          suite;
    std::string          name;
    std::function<void()> fn;
};

inline std::vector<TestCase>& registry() {
    static std::vector<TestCase> r;
    return r;
}

inline int& currentFailures() {  // failed checks within the running test
    static int f = 0;
    return f;
}

struct Registrar {
    Registrar(const char* s, const char* n, std::function<void()> f) {
        registry().push_back({s, n, std::move(f)});
    }
};

// Thrown by ASSERT_* to abort the current test (EXPECT_* just records).
struct FatalAssertion {};

inline void reportFail(const char* file, int line, const std::string& expr) {
    std::printf("    [  FAILED  ] %s:%d\n        %s\n", file, line, expr.c_str());
    ++currentFailures();
}

}  // namespace rts_test

// ---- Test registration -----------------------------------------------------
#define TEST(suite, name)                                                      \
    static void suite##_##name##_body();                                       \
    static ::rts_test::Registrar suite##_##name##_registrar(                   \
        #suite, #name, &suite##_##name##_body);                                \
    static void suite##_##name##_body()

// ---- Assertion core --------------------------------------------------------
#define RTS_TEST_CHECK(cond, fatal, expr)                                      \
    do {                                                                       \
        if (!(cond)) {                                                         \
            ::rts_test::reportFail(__FILE__, __LINE__, (expr));                \
            if (fatal) throw ::rts_test::FatalAssertion{};                     \
        }                                                                      \
    } while (0)

#define EXPECT_TRUE(x)     RTS_TEST_CHECK((x), false, "EXPECT_TRUE(" #x ")")
#define EXPECT_FALSE(x)    RTS_TEST_CHECK(!(x), false, "EXPECT_FALSE(" #x ")")
#define ASSERT_TRUE(x)     RTS_TEST_CHECK((x), true,  "ASSERT_TRUE(" #x ")")
#define ASSERT_FALSE(x)    RTS_TEST_CHECK(!(x), true,  "ASSERT_FALSE(" #x ")")

#define EXPECT_EQ(a, b)    RTS_TEST_CHECK((a) == (b), false, "EXPECT_EQ(" #a ", " #b ")")
#define EXPECT_NE(a, b)    RTS_TEST_CHECK((a) != (b), false, "EXPECT_NE(" #a ", " #b ")")
#define EXPECT_GT(a, b)    RTS_TEST_CHECK((a) >  (b), false, "EXPECT_GT(" #a ", " #b ")")
#define EXPECT_GE(a, b)    RTS_TEST_CHECK((a) >= (b), false, "EXPECT_GE(" #a ", " #b ")")
#define EXPECT_LT(a, b)    RTS_TEST_CHECK((a) <  (b), false, "EXPECT_LT(" #a ", " #b ")")
#define EXPECT_LE(a, b)    RTS_TEST_CHECK((a) <= (b), false, "EXPECT_LE(" #a ", " #b ")")
#define ASSERT_EQ(a, b)    RTS_TEST_CHECK((a) == (b), true,  "ASSERT_EQ(" #a ", " #b ")")
#define ASSERT_NE(a, b)    RTS_TEST_CHECK((a) != (b), true,  "ASSERT_NE(" #a ", " #b ")")

#define EXPECT_NEAR(a, b, tol) \
    RTS_TEST_CHECK(std::fabs((double)(a) - (double)(b)) <= (tol), false, \
                   "EXPECT_NEAR(" #a ", " #b ", " #tol ")")
#define EXPECT_DOUBLE_EQ(a, b) EXPECT_NEAR((a), (b), 1e-9)

// ---- Runner ----------------------------------------------------------------
namespace testing {
inline void InitGoogleTest(int*, char**) {}
inline void InitGoogleTest(int*, wchar_t**) {}
}  // namespace testing

inline int RUN_ALL_TESTS() {
    int failedTests = 0;
    int passedTests = 0;
    std::printf("[==========] Running %zu tests.\n", ::rts_test::registry().size());
    for (auto& tc : ::rts_test::registry()) {
        std::printf("[ RUN      ] %s.%s\n", tc.suite.c_str(), tc.name.c_str());
        ::rts_test::currentFailures() = 0;
        try {
            tc.fn();
        } catch (const ::rts_test::FatalAssertion&) {
            // fatal assert already reported
        } catch (const std::exception& e) {
            ::rts_test::reportFail(__FILE__, __LINE__,
                                   std::string("unexpected exception: ") + e.what());
        } catch (...) {
            ::rts_test::reportFail(__FILE__, __LINE__, "unexpected non-std exception");
        }
        if (::rts_test::currentFailures() == 0) {
            std::printf("[       OK ] %s.%s\n", tc.suite.c_str(), tc.name.c_str());
            ++passedTests;
        } else {
            std::printf("[  FAILED  ] %s.%s\n", tc.suite.c_str(), tc.name.c_str());
            ++failedTests;
        }
    }
    std::printf("[==========] %d passed, %d failed.\n", passedTests, failedTests);
    return failedTests == 0 ? 0 : 1;
}
