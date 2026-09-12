/*
 * main.c — Entrypoint: parse config, wire the engine, run headless or with GUI.
 *
 * Configuration precedence (see docs/TECH-NOTES.md §3.4):
 *     CLI flag  >  environment variable  >  compiled-in default.
 */
#include "common/config.h"
#include "common/log.h"
#include "common/types.h"
#include "core/cpu.h"
#include "core/decoder.h"
#include "core/registers.h"
#include "core/timing.h"
#include "loader/loader.h"
#include "gui/visualizer.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* A tiny built-in program for --selftest:
 *   mov eax, 5 ; mov ecx, 7 ; add eax, ecx ; hlt    → EAX should end as 12. */
static const uint8_t k_selftest[] = {
    0xB8, 0x05, 0x00, 0x00, 0x00,  /* mov eax, 5      */
    0xB9, 0x07, 0x00, 0x00, 0x00,  /* mov ecx, 7      */
    0x01, 0xC8,                    /* add eax, ecx    */
    0xF4                           /* hlt             */
};

typedef struct {
    const char *program;    /* flat binary to load           */
    const char *restore;    /* snapshot to restore           */
    const char *snapshot;   /* snapshot to save at exit      */
    cpu_model_t model;
    size_t      mem_size;
    uint64_t    load_addr;
    uint64_t    max_steps;
    bool        headless;
    bool        selftest;
} options_t;

static uint64_t env_u64(const char *name, uint64_t fallback) {
    const char *v = getenv(name);
    return (v && *v) ? strtoull(v, NULL, 0) : fallback;
}

static void load_env_defaults(options_t *o) {
    o->model     = CPUEMU_DEFAULT_MODEL;
    o->mem_size  = (size_t)env_u64("CPUEMU_MEM_SIZE", CPUEMU_DEFAULT_MEM_SIZE);
    o->load_addr = env_u64("CPUEMU_LOAD_ADDR", CPUEMU_DEFAULT_LOAD_ADDR);
    o->max_steps = env_u64("CPUEMU_MAX_STEPS", CPUEMU_DEFAULT_MAX_STEPS);
    o->headless  = env_u64("CPUEMU_HEADLESS", 0) != 0;

    const char *m = getenv("CPUEMU_CPU_MODEL");
    if (m && *m) {
        cpu_model_t parsed = cpu_model_from_id(m);
        if (parsed != CPU_MODEL_COUNT) o->model = parsed;
    }
}

static void usage(const char *prog) {
    printf(
        "Custom CPU Emulator v%s — x86-64 subset emulator\n\n"
        "Usage: %s [options]\n\n"
        "  -p, --program FILE    flat binary to load and execute\n"
        "  -m, --model ID        8086|i486|pentium|core2|skylake (default: %s)\n"
        "      --mem-size N       guest RAM in bytes (default: %u)\n"
        "      --load-addr ADDR   load/entry address (default: 0x%llx)\n"
        "      --max-steps N      stop after N instructions (0 = unlimited)\n"
        "      --headless         run without the SDL2 window\n"
        "      --restore FILE     restore a .ces snapshot before running\n"
        "      --snapshot FILE    save a .ces snapshot on exit\n"
        "      --selftest         run a tiny built-in program and verify\n"
        "      --log-level L      trace|debug|info|warn|error|fatal\n"
        "  -h, --help            show this help\n\n"
        "Environment overrides: CPUEMU_LOG_LEVEL, CPUEMU_CPU_MODEL,\n"
        "  CPUEMU_MEM_SIZE, CPUEMU_HEADLESS, CPUEMU_MAX_STEPS, CPUEMU_LOAD_ADDR\n",
        CPUEMU_VERSION_STRING, prog, cpu_model_id(CPUEMU_DEFAULT_MODEL),
        CPUEMU_DEFAULT_MEM_SIZE, (unsigned long long)CPUEMU_DEFAULT_LOAD_ADDR);
}

/* Returns 0 on success, >0 to exit with that code, <0 to continue (parsed ok). */
static int parse_args(int argc, char **argv, options_t *o) {
    for (int i = 1; i < argc; ++i) {
        const char *a = argv[i];
        #define NEXT() (i + 1 < argc ? argv[++i] : NULL)
        if (!strcmp(a, "-h") || !strcmp(a, "--help")) { usage(argv[0]); return 0; }
        else if (!strcmp(a, "-p") || !strcmp(a, "--program")) o->program = NEXT();
        else if (!strcmp(a, "--restore"))  o->restore  = NEXT();
        else if (!strcmp(a, "--snapshot")) o->snapshot = NEXT();
        else if (!strcmp(a, "--headless")) o->headless = true;
        else if (!strcmp(a, "--selftest")) o->selftest = true;
        else if (!strcmp(a, "--mem-size"))  { const char *v = NEXT(); if (v) o->mem_size  = (size_t)strtoull(v, NULL, 0); }
        else if (!strcmp(a, "--load-addr")) { const char *v = NEXT(); if (v) o->load_addr = strtoull(v, NULL, 0); }
        else if (!strcmp(a, "--max-steps")) { const char *v = NEXT(); if (v) o->max_steps = strtoull(v, NULL, 0); }
        else if (!strcmp(a, "-m") || !strcmp(a, "--model")) {
            const char *v = NEXT();
            cpu_model_t parsed = v ? cpu_model_from_id(v) : CPU_MODEL_COUNT;
            if (parsed == CPU_MODEL_COUNT) { fprintf(stderr, "unknown model '%s'\n", v ? v : ""); return 2; }
            o->model = parsed;
        }
        else if (!strcmp(a, "--log-level")) {
            const char *v = NEXT();
            for (int l = LOG_TRACE; v && l <= LOG_FATAL; ++l) {
                if (!strcmp(v, log_level_name((log_level_t)l))) { log_set_level((log_level_t)l); break; }
            }
        }
        else { fprintf(stderr, "unknown option '%s' (try --help)\n", a); return 2; }
        #undef NEXT
    }
    return -1;
}

static void print_flag(uint64_t rflags, flag_mask_t m, char c) {
    putchar((rflags & m) ? c : '-');
}

static void dump_state(const cpu_t *cpu) {
    static const reg_id_t order[REG_COUNT] = {
        REG_RAX, REG_RBX, REG_RCX, REG_RDX, REG_RSI, REG_RDI, REG_RBP, REG_RSP,
        REG_R8, REG_R9, REG_R10, REG_R11, REG_R12, REG_R13, REG_R14, REG_R15
    };
    printf("\n=== machine state =========================================\n");
    printf("model : %s   instret: %llu   cycles: %llu   (~%.1f ns)\n",
           cpu_model_name(cpu->model), (unsigned long long)cpu->instret,
           (unsigned long long)cpu->timing.total_cycles,
           timing_elapsed_ns(&cpu->timing));
    printf("status: %s%s\n", emu_status_str(cpu->last_status),
           cpu->halted ? " [halted]" : "");

    for (int i = 0; i < REG_COUNT; ++i) {
        reg_id_t r = order[i];
        printf("  %-3s = 0x%016llx", reg_name(r, WIDTH_QWORD),
               (unsigned long long)cpu->regs.gpr[r]);
        if (i % 2 == 1) putchar('\n');
    }
    printf("  rip = 0x%016llx\n", (unsigned long long)cpu->regs.rip);

    printf("  flags [");
    print_flag(cpu->regs.rflags, FLAG_OF, 'O');
    print_flag(cpu->regs.rflags, FLAG_SF, 'S');
    print_flag(cpu->regs.rflags, FLAG_ZF, 'Z');
    print_flag(cpu->regs.rflags, FLAG_AF, 'A');
    print_flag(cpu->regs.rflags, FLAG_PF, 'P');
    print_flag(cpu->regs.rflags, FLAG_CF, 'C');
    printf("]\n");

    char dis[80];
    if (disasm_format(&cpu->last_insn, dis, sizeof dis) > 0) {
        printf("  last : %s\n", dis);
    }
    printf("===========================================================\n");
}

int main(int argc, char **argv) {
    log_init_from_env();

    options_t opt;
    memset(&opt, 0, sizeof opt);
    load_env_defaults(&opt);

    int pa = parse_args(argc, argv, &opt);
    if (pa >= 0) {
        return pa; /* help shown (0) or parse error (>0) */
    }

    if (opt.program == NULL && opt.restore == NULL && !opt.selftest) {
        usage(argv[0]);
        return 1;
    }

    cpu_t *cpu = cpu_create(opt.mem_size, opt.model);
    if (cpu == NULL) {
        log_fatal("failed to allocate CPU (%zu bytes RAM)", opt.mem_size);
        return 1;
    }

    emu_status_t st = EMU_OK;
    if (opt.restore) {
        st = loader_load_state(cpu, opt.restore);
    } else if (opt.selftest) {
        st = cpu_load_program(cpu, k_selftest, sizeof k_selftest, opt.load_addr);
    } else {
        st = loader_load_flat_binary(cpu, opt.program, opt.load_addr);
    }
    if (st != EMU_OK) {
        log_fatal("load failed: %s", emu_status_str(st));
        cpu_destroy(cpu);
        return 1;
    }

    bool headless = opt.headless || !vis_available();

    if (headless) {
        st = cpu_run(cpu, opt.max_steps);
        dump_state(cpu);
    } else {
        visualizer_t *vis = vis_create(cpu, "Custom CPU Emulator");
        if (vis == NULL) {
            log_warn("GUI unavailable; falling back to headless");
            st = cpu_run(cpu, opt.max_steps);
        } else {
            while (vis_frame(vis)) { /* loop until window closed */ }
            vis_destroy(vis);
        }
        dump_state(cpu);
    }

    if (opt.snapshot) {
        loader_save_state(cpu, opt.snapshot);
    }

    int rc = 0;
    if (opt.selftest) {
        uint64_t eax = reg_read(&cpu->regs, REG_RAX, WIDTH_DWORD);
        if (eax == 12 && cpu->halted) {
            printf("[selftest] PASS (eax=12, halted)\n");
        } else {
            printf("[selftest] FAIL (eax=%llu, halted=%d)\n",
                   (unsigned long long)eax, (int)cpu->halted);
            rc = 1;
        }
    } else if (st != EMU_OK && st != EMU_ERR_HALT) {
        rc = 1;
    }

    cpu_destroy(cpu);
    return rc;
}
