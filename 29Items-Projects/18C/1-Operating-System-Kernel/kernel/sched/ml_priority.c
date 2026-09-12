/*
 * ml_priority.c — Fixed-point linear model for process priority.
 *
 * score = w0*cpu_burst + w1*io_wait + w2*age + w3*nice + bias   (all Q16.16)
 *
 * Intuition encoded by the default weights:
 *   - high CPU burst  -> CPU-bound -> LOWER priority (larger band number)
 *   - high I/O wait   -> interactive -> HIGHER priority (smaller band)
 *   - high age        -> waited long -> HIGHER priority (anti-starvation)
 *
 * All integer math: no FPU state to save across context switches (TECH-NOTES
 * §3.6). The model is intentionally tiny and explainable.
 */
#include "../include/kernel.h"
#include "../include/ml_sched.h"

/* ---- Q16.16 fixed-point helpers --------------------------------- */
mlfixed_t mlf_from_int(int32_t v) { return v << MLF_FRAC_BITS; }
int32_t   mlf_to_int(mlfixed_t v) { return v >> MLF_FRAC_BITS; }

mlfixed_t mlf_mul(mlfixed_t a, mlfixed_t b)
{
    /* 64-bit intermediate avoids overflow before the shift. */
    return (mlfixed_t)(((int64_t)a * (int64_t)b) >> MLF_FRAC_BITS);
}

void ml_model_init(ml_model_t *m)
{
    /* Hand-tuned defaults (scaled small; features can be large tick counts).
     * Signs follow the intuition documented above. */
    m->weights[0] = mlf_from_int(1) / 64;   /* cpu_burst -> +band (deprioritize) */
    m->weights[1] = -(mlf_from_int(1) / 96);/* io_wait   -> -band (prioritize)   */
    m->weights[2] = -(mlf_from_int(1) / 128);/* age      -> -band (anti-starve)  */
    m->weights[3] = mlf_from_int(1) / 8;    /* nice      -> +band                */
    m->bias       = mlf_from_int(2);        /* center around mid band            */
    m->learning_rate = mlf_from_int(1) / 256;
    KLOG_DEBUG("ml: linear priority model initialized");
}

static mlfixed_t feature(const sched_features_t *f, int i)
{
    switch (i) {
        case 0: return mlf_from_int((int32_t)f->cpu_burst_ema);
        case 1: return mlf_from_int((int32_t)f->io_wait_ema);
        case 2: return mlf_from_int((int32_t)f->age_ticks);
        case 3: return mlf_from_int(f->nice);
        default: return 0;
    }
}

static mlfixed_t raw_score(const ml_model_t *m, const sched_features_t *f)
{
    mlfixed_t acc = m->bias;
    for (int i = 0; i < MLF_NFEATURES; i++)
        acc += mlf_mul(m->weights[i], feature(f, i));
    return acc;
}

uint8_t ml_priority_score(const ml_model_t *m, const sched_features_t *f)
{
    int32_t band = mlf_to_int(raw_score(m, f));
    if (band < 0)            band = 0;
    if (band >= SCHED_NPRIO) band = SCHED_NPRIO - 1;
    return (uint8_t)band;
}

void ml_model_update(ml_model_t *m, const sched_features_t *f,
                     uint8_t observed_band)
{
    /* Perceptron-style update: nudge weights to reduce (pred - observed).
     *   w_i += lr * error * x_i ;  bias += lr * error
     * `error` is in band units; keep it in fixed-point throughout. */
    int32_t   predicted = ml_priority_score(m, f);
    mlfixed_t error     = mlf_from_int((int32_t)observed_band - predicted);
    mlfixed_t step      = mlf_mul(m->learning_rate, error);

    for (int i = 0; i < MLF_NFEATURES; i++)
        m->weights[i] += mlf_mul(step, feature(f, i));
    m->bias += step;
}
