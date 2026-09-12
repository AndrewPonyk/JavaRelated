#pragma once
// Minimal GoogleTest-compatible shim.
//
// Implements the subset of the GoogleTest API the engine's suites use, so tests
// compile and run with ZERO third-party dependencies. When real GoogleTest is
// available (CI/vcpkg), tests/CMakeLists.txt links that instead and this header is
// not on the include path. Keep the macro surface compatible with upstream gtest.

#include <cmath>
#include <cstdio>
#include <exception>
#include <functional>
#include <string>
#include <vector>

namespace gtest_shim {

struct TestCase {
    std::string                 suite;
    std::string                 name;
    std::function<void(bool&)>  body;
};

inline std::vector<TestCase>& registry() {
    static std::vector<TestCase> r;
    return r;
}

struct Registrar {
    Registrar(const char* suite, const char* name, std::function<void(bool&)> body) {
        registry().push_back({suite, name, std::move(body)});
    }
};

inline int runAll() {
    int passed = 0;
    int failed = 0;
    std::printf("[==========] Running %d test(s).\n", static_cast<int>(registry().size()));
    for (const auto& tc : registry()) {
        bool ok = true;
        std::printf("[ RUN      ] %s.%s\n", tc.suite.c_str(), tc.name.c_str());
        try {
            tc.body(ok);
        } catch (const std::exception& e) {
            ok = false;
            std::printf("    unhandled exception: %s\n", e.what());
        } catch (...) {
            ok = false;
            std::printf("    unhandled unknown exception\n");
        }
        if (ok) {
            std::printf("[       OK ] %s.%s\n", tc.suite.c_str(), tc.name.c_str());
            ++passed;
        } else {
            std::printf("[  FAILED  ] %s.%s\n", tc.suite.c_str(), tc.name.c_str());
            ++failed;
        }
    }
    std::printf("[==========] %d passed, %d failed.\n", passed, failed);
    return failed == 0 ? 0 : 1;
}

} // namespace gtest_shim

// ---- Registration ----
#define TEST(suite, name)                                                                  \
    static void suite##_##name##_body(bool& _gtest_ok);                                     \
    static ::gtest_shim::Registrar suite##_##name##_registrar(                              \
        #suite, #name, [](bool& _ok) { suite##_##name##_body(_ok); });                     \
    static void suite##_##name##_body([[maybe_unused]] bool& _gtest_ok)

// ---- Failure reporting ----
#define GTEST_REPORT_(expr)                                                                 \
    do {                                                                                     \
        std::printf("    %s:%d: failure: %s\n", __FILE__, __LINE__, expr);                  \
        _gtest_ok = false;                                                                   \
    } while (false)

// ---- EXPECT_* (non-fatal) ----
#define EXPECT_TRUE(c)   do { if (!(c)) GTEST_REPORT_("EXPECT_TRUE(" #c ")"); } while (false)
#define EXPECT_FALSE(c)  do { if ((c))  GTEST_REPORT_("EXPECT_FALSE(" #c ")"); } while (false)
#define EXPECT_EQ(a, b)  do { if (!((a) == (b))) GTEST_REPORT_("EXPECT_EQ(" #a ", " #b ")"); } while (false)
#define EXPECT_NE(a, b)  do { if (!((a) != (b))) GTEST_REPORT_("EXPECT_NE(" #a ", " #b ")"); } while (false)
#define EXPECT_GT(a, b)  do { if (!((a) >  (b))) GTEST_REPORT_("EXPECT_GT(" #a ", " #b ")"); } while (false)
#define EXPECT_LT(a, b)  do { if (!((a) <  (b))) GTEST_REPORT_("EXPECT_LT(" #a ", " #b ")"); } while (false)
#define EXPECT_GE(a, b)  do { if (!((a) >= (b))) GTEST_REPORT_("EXPECT_GE(" #a ", " #b ")"); } while (false)
#define EXPECT_LE(a, b)  do { if (!((a) <= (b))) GTEST_REPORT_("EXPECT_LE(" #a ", " #b ")"); } while (false)
#define EXPECT_FLOAT_EQ(a, b)                                                               \
    do {                                                                                     \
        const double _da = static_cast<double>(a);                                          \
        const double _db = static_cast<double>(b);                                          \
        if (std::fabs(_da - _db) > 1e-4 * (1.0 + std::fabs(_db)))                           \
            GTEST_REPORT_("EXPECT_FLOAT_EQ(" #a ", " #b ")");                               \
    } while (false)
#define EXPECT_NEAR(a, b, tol)                                                              \
    do {                                                                                     \
        if (std::fabs(static_cast<double>(a) - static_cast<double>(b)) > (tol))             \
            GTEST_REPORT_("EXPECT_NEAR(" #a ", " #b ")");                                   \
    } while (false)

// ---- ASSERT_* (fatal: return from the test body) ----
#define ASSERT_TRUE(c)  do { if (!(c)) { GTEST_REPORT_("ASSERT_TRUE(" #c ")"); return; } } while (false)
#define ASSERT_FALSE(c) do { if ((c))  { GTEST_REPORT_("ASSERT_FALSE(" #c ")"); return; } } while (false)
#define ASSERT_EQ(a, b) do { if (!((a) == (b))) { GTEST_REPORT_("ASSERT_EQ(" #a ", " #b ")"); return; } } while (false)
#define ASSERT_NE(a, b) do { if (!((a) != (b))) { GTEST_REPORT_("ASSERT_NE(" #a ", " #b ")"); return; } } while (false)

#define RUN_ALL_TESTS() ::gtest_shim::runAll()
