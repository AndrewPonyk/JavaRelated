/*
 * ml_sched.h — Simple ML-based process priority model.
 *
 * The model is a tiny linear predictor in FIXED-POINT integer math (Q16.16):
 *
 *     score = w0*cpu_burst + w1*io_wait + w2*age + w3*nice + bias
 *
 * It maps a feature vector to a priority band [0, SCHED_NPRIO). It is advisory:
 * the scheduler clamps the result so a bad prediction can never starve a task.
 * Weights can be updated online (perceptron-style) from observed behavior.
 *
 * Why fixed-point? Using x87/SSE in ring 0 means saving FP state across context
 * switches. Integer math sidesteps that entirely (see TECH-NOTES §3.6).
 */
#ifndef KERNEL_ML_SCHED_H
#define KERNEL_ML_SCHED_H

#include "types.h"
#include "sched.h"

#define MLF_FRAC_BITS  16
#define MLF_ONE        (1 << MLF_FRAC_BITS)        /* 1.0 in Q16.16 */
#define MLF_NFEATURES  4

typedef int32_t mlfixed_t;                          /* Q16.16 */

typedef struct ml_model {
    mlfixed_t weights[MLF_NFEATURES];
    mlfixed_t bias;
    mlfixed_t learning_rate;
} ml_model_t;

/* Initialize with sane default weights. */
void      ml_model_init(ml_model_t *m);

/* Predict a priority band [0, SCHED_NPRIO) for the given features. */
uint8_t   ml_priority_score(const ml_model_t *m, const sched_features_t *f);

/* Online update toward an observed "good" band (e.g. derived from fairness). */
void      ml_model_update(ml_model_t *m, const sched_features_t *f,
                          uint8_t observed_band);

/* Fixed-point helpers (exposed for unit tests). */
mlfixed_t mlf_from_int(int32_t v);
int32_t   mlf_to_int(mlfixed_t v);
mlfixed_t mlf_mul(mlfixed_t a, mlfixed_t b);

#endif /* KERNEL_ML_SCHED_H */
