/*
 * test_ml_priority.c — Host unit tests for the fixed-point ML priority model.
 *
 * This is the cleanest module to test: pure integer math, no hardware. It
 * compiles ml_priority.c directly (see tests/Makefile target).
 */
#include "test_framework.h"
#include "../kernel/include/ml_sched.h"

/* The kernel's klog/kprintf are not linked into the host test; stub them. */
int kprintf(const char *fmt, ...) { (void)fmt; return 0; }
void klog(int level, const char *fmt, ...) { (void)level; (void)fmt; }

TEST(fixed_point_roundtrip)
{
    ASSERT_EQ(mlf_to_int(mlf_from_int(7)), 7);
    ASSERT_EQ(mlf_to_int(mlf_mul(mlf_from_int(3), mlf_from_int(4))), 12);
}

TEST(score_within_band_range)
{
    ml_model_t m;
    ml_model_init(&m);

    sched_features_t f = { .cpu_burst_ema = 100, .io_wait_ema = 0,
                           .age_ticks = 0, .nice = 0 };
    uint8_t band = ml_priority_score(&m, &f);
    ASSERT_TRUE(band < SCHED_NPRIO);          /* always clamped into range */
}

TEST(cpu_bound_is_deprioritized)
{
    ml_model_t m;
    ml_model_init(&m);

    sched_features_t cpu_bound = { .cpu_burst_ema = 400, .io_wait_ema = 0,
                                   .age_ticks = 0, .nice = 0 };
    sched_features_t io_bound  = { .cpu_burst_ema = 0, .io_wait_ema = 400,
                                   .age_ticks = 0, .nice = 0 };

    /* Lower band number = higher priority. I/O bound should rank ahead. */
    ASSERT_TRUE(ml_priority_score(&m, &io_bound) <=
                ml_priority_score(&m, &cpu_bound));
}

TEST(online_update_moves_toward_target)
{
    ml_model_t m;
    ml_model_init(&m);

    sched_features_t f = { .cpu_burst_ema = 50, .io_wait_ema = 10,
                           .age_ticks = 5, .nice = 0 };

    /* Train repeatedly toward band 0; prediction should not drift away. */
    uint8_t before = ml_priority_score(&m, &f);
    for (int i = 0; i < 50; i++) ml_model_update(&m, &f, 0);
    uint8_t after = ml_priority_score(&m, &f);

    ASSERT_TRUE(after <= before);             /* converged toward target 0 */
}

int main(void)
{
    printf("ml_priority tests:\n");
    RUN(fixed_point_roundtrip);
    RUN(score_within_band_range);
    RUN(cpu_bound_is_deprioritized);
    RUN(online_update_moves_toward_target);
    return test_summary();
}
