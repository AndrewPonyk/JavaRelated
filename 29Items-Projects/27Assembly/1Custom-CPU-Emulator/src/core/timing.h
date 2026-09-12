/*
 * timing.h — Per-microarchitecture cycle-cost model (the "performance" feature).
 *
 * After each instruction retires, the CPU asks the timing model how many clock
 * cycles that instruction would have cost on the *selected* microarchitecture.
 * Costs are data-driven latency/throughput tables (see timing.c) so adding a new
 * CPU model is adding a table, not changing logic.
 *
 * IMPORTANT: these are deliberately simplified *estimates* for teaching, not a
 * substitute for a real performance simulator (no full pipeline/cache model in
 * v1 — that is on the roadmap). Tables cite their sources in timing.c.
 */
#ifndef CPUEMU_CORE_TIMING_H
#define CPUEMU_CORE_TIMING_H

#include "common/types.h"
#include "core/decoder.h"

typedef struct {
    cpu_model_t model;
    uint64_t    total_cycles; /* accumulated estimated cycles               */
    double      clock_hz;     /* nominal clock for ns/elapsed-time estimates */
} timing_state_t;

/* Initialize for a given model (sets nominal clock + zeroes the counter). */
void timing_init(timing_state_t *t, cpu_model_t model);

/* Estimated cycle cost of a single decoded instruction on `model`. */
uint32_t timing_instruction_cost(cpu_model_t model, const instruction_t *insn);

/* Add one instruction's cost to the running total. */
void timing_account(timing_state_t *t, const instruction_t *insn);

/* Estimated wall-clock nanoseconds for the cycles accumulated so far. */
double timing_elapsed_ns(const timing_state_t *t);

/* Human-readable model name ("Intel Skylake", ...) and short id ("skylake"). */
const char *cpu_model_name(cpu_model_t model);
const char *cpu_model_id(cpu_model_t model);

/* Parse a short id ("8086"|"i486"|"pentium"|"core2"|"skylake"). Returns
 * CPU_MODEL_COUNT if unrecognized. */
cpu_model_t cpu_model_from_id(const char *id);

#endif /* CPUEMU_CORE_TIMING_H */
