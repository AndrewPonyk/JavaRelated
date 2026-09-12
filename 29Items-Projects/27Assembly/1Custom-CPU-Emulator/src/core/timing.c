/*
 * timing.c — Simplified per-microarchitecture cycle model.
 *
 * Each instruction is mapped to a coarse "class", and each modeled CPU has a
 * latency for that class. Numbers are illustrative teaching values inspired by
 * published tables (Agner Fog's instruction tables; Intel/AMD optimization
 * manuals) — they capture the *relative* evolution (8086's microcoded MUL/DIV
 * costing dozens of cycles vs. a single-cycle ALU op on Skylake) without
 * claiming cycle-exactness. Replace with a real pipeline model later (roadmap).
 */
#include "core/timing.h"

#include <string.h>

/* Coarse instruction classes for costing. */
typedef enum {
    CLS_MOV = 0, CLS_ALU, CLS_BRANCH, CLS_MEM,
    CLS_MUL, CLS_DIV, CLS_STACK, CLS_INT, CLS_NOP, CLS_COUNT
} insn_class_t;

/* cost[model][class] in clock cycles (latency estimate). */
static const uint32_t k_cost[CPU_MODEL_COUNT][CLS_COUNT] = {
    /*               MOV ALU  BR  MEM   MUL    DIV  STK  INT  NOP */
    [CPU_MODEL_8086]    = {  2,  3,  16,  10,  118,  162,  11,  51,  3 },
    [CPU_MODEL_I486]    = {  1,  1,   3,   2,   13,   40,   2,  26,  1 },
    [CPU_MODEL_PENTIUM] = {  1,  1,   1,   2,   10,   25,   1,  16,  1 },
    [CPU_MODEL_CORE2]   = {  1,  1,   1,   2,    5,   22,   1,  10,  1 },
    [CPU_MODEL_SKYLAKE] = {  1,  1,   1,   1,    3,   26,   1,   8,  1 },
};

/* Nominal clocks (Hz) for ns estimates. */
static const double k_clock_hz[CPU_MODEL_COUNT] = {
    [CPU_MODEL_8086]    = 5.0e6,    /*   5 MHz */
    [CPU_MODEL_I486]    = 33.0e6,   /*  33 MHz */
    [CPU_MODEL_PENTIUM] = 100.0e6,  /* 100 MHz */
    [CPU_MODEL_CORE2]   = 2.4e9,    /* 2.4 GHz */
    [CPU_MODEL_SKYLAKE] = 3.6e9,    /* 3.6 GHz */
};

static const char *const k_name[CPU_MODEL_COUNT] = {
    "Intel 8086", "Intel i486", "Intel Pentium", "Intel Core 2", "Intel Skylake"
};
static const char *const k_id[CPU_MODEL_COUNT] = {
    "8086", "i486", "pentium", "core2", "skylake"
};

static insn_class_t classify(opcode_t op) {
    switch (op) {
        case OP_NOP:  return CLS_NOP;
        case OP_MOV:  case OP_LEA: return CLS_MOV;
        case OP_MUL:  case OP_IMUL: return CLS_MUL;
        case OP_DIV:  case OP_IDIV: return CLS_DIV;
        case OP_PUSH: case OP_POP:  return CLS_STACK;
        case OP_JMP:  case OP_JE:  case OP_JNE: case OP_JL: case OP_JLE:
        case OP_JG:   case OP_JGE: case OP_CALL: case OP_RET: case OP_LOOP:
            return CLS_BRANCH;
        case OP_INT:  case OP_IRET: case OP_HLT: return CLS_INT;
        default:      return CLS_ALU;
    }
}

void timing_init(timing_state_t *t, cpu_model_t model) {
    if (t == NULL) {
        return;
    }
    if (model >= CPU_MODEL_COUNT) {
        model = CPU_MODEL_SKYLAKE;
    }
    t->model = model;
    t->total_cycles = 0;
    t->clock_hz = k_clock_hz[model];
}

uint32_t timing_instruction_cost(cpu_model_t model, const instruction_t *insn) {
    if (insn == NULL || model >= CPU_MODEL_COUNT) {
        return 1;
    }
    insn_class_t cls = classify(insn->op);
    uint32_t cost = k_cost[model][cls];

    /* A memory operand adds the model's memory-access penalty. */
    if (insn->dst.kind == OPERAND_MEM || insn->src.kind == OPERAND_MEM) {
        cost += k_cost[model][CLS_MEM];
    }
    return cost;
}

void timing_account(timing_state_t *t, const instruction_t *insn) {
    if (t == NULL) {
        return;
    }
    t->total_cycles += timing_instruction_cost(t->model, insn);
}

double timing_elapsed_ns(const timing_state_t *t) {
    if (t == NULL || t->clock_hz <= 0.0) {
        return 0.0;
    }
    return (double)t->total_cycles / t->clock_hz * 1.0e9;
}

const char *cpu_model_name(cpu_model_t model) {
    return (model < CPU_MODEL_COUNT) ? k_name[model] : "unknown";
}

const char *cpu_model_id(cpu_model_t model) {
    return (model < CPU_MODEL_COUNT) ? k_id[model] : "unknown";
}

cpu_model_t cpu_model_from_id(const char *id) {
    if (id == NULL) {
        return CPU_MODEL_COUNT;
    }
    for (int i = 0; i < CPU_MODEL_COUNT; ++i) {
        if (strcmp(id, k_id[i]) == 0) {
            return (cpu_model_t)i;
        }
    }
    return CPU_MODEL_COUNT;
}
