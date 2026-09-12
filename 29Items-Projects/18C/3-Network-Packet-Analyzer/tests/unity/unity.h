/* SPDX-License-Identifier: MIT
 *
 * tests/unity/unity.h — PLACEHOLDER.
 *
 * This project ships its own zero-dependency micro-framework in
 * tests/test_common.h, which is sufficient for the current suite.
 *
 * If/when the test suite outgrows it (parameterized tests, fixtures, richer
 * assertions, JUnit XML for CI), vendor ThrowTheSwitch/Unity here:
 *
 *     curl -L https://raw.githubusercontent.com/ThrowTheSwitch/Unity/master/src/unity.h -o tests/unity/unity.h
 *     curl -L https://raw.githubusercontent.com/ThrowTheSwitch/Unity/master/src/unity.c -o tests/unity/unity.c
 *     curl -L https://raw.githubusercontent.com/ThrowTheSwitch/Unity/master/src/unity_internals.h -o tests/unity/unity_internals.h
 *
 * Then migrate test bodies from the ASSERT_* macros to Unity's TEST_ASSERT_*.
 * No production code changes are required — the dissectors/ring/anomaly engine
 * are already framework-agnostic.
 */
#ifndef NPA_TESTS_UNITY_PLACEHOLDER_H
#define NPA_TESTS_UNITY_PLACEHOLDER_H
#error "unity.h is a placeholder — see tests/test_common.h, or vendor Unity here."
#endif
