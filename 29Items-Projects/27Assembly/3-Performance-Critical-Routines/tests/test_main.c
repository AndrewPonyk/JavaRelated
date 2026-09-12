/* test_main.c — runner. Exit non-zero on any failure (CI gate). */
#include "perflib/perflib.h"
#include "test_framework.h"

int g_tests_run = 0;
int g_tests_failed = 0;

void test_matrix(void);
void test_string(void);

int main(void)
{
    perflib_init();
    fprintf(stderr, "%s\n", perflib_version_string());

    RUN_SUITE(test_matrix);
    RUN_SUITE(test_string);

    fprintf(stderr, "\n%d checks run, %d failed\n", g_tests_run, g_tests_failed);
    return g_tests_failed ? 1 : 0;
}
