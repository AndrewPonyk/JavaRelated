/*
 * config.h — Compile-time configuration, version, and feature flags.
 *
 * Runtime configuration (log level, model, memory size, ...) is read from
 * environment variables / CLI flags; these are only the *defaults* and the
 * build-time switches. Precedence: CLI flag > env var > these defaults.
 */
#ifndef CPUEMU_COMMON_CONFIG_H
#define CPUEMU_COMMON_CONFIG_H

#include "common/types.h" /* CPU_MODEL_* used by CPUEMU_DEFAULT_MODEL below */

/* ----- Versioning (SemVer) --------------------------------------------- */
#define CPUEMU_VERSION_MAJOR 0
#define CPUEMU_VERSION_MINOR 1
#define CPUEMU_VERSION_PATCH 0
#define CPUEMU_VERSION_STRING "0.1.0"

/* ----- Runtime defaults ------------------------------------------------- */
#define CPUEMU_DEFAULT_MEM_SIZE   (1u << 20)   /* 1 MiB of guest RAM         */
#define CPUEMU_DEFAULT_LOAD_ADDR  0x1000ULL    /* where flat binaries land   */
#define CPUEMU_DEFAULT_MODEL      CPU_MODEL_SKYLAKE
#define CPUEMU_DEFAULT_MAX_STEPS  0ULL         /* 0 = unlimited              */

/* ----- Feature flags (override on the make command line) ---------------- */
/* Build the SDL2 visualizer. `make` sets this when SDL2 is detected; the
 * headless core (CI/tests) compiles with CPUEMU_ENABLE_GUI=0.              */
#ifndef CPUEMU_ENABLE_GUI
#define CPUEMU_ENABLE_GUI 0
#endif

/* Route the ALU ADD through the hand-written NASM helper (System V ABI,
 * Linux-first). Off by default so the pure-C build never needs the assembler. */
#ifndef CPUEMU_USE_ASM_ALU
#define CPUEMU_USE_ASM_ALU 0
#endif

/* ----- Snapshot ("persistence") format ---------------------------------- */
#define CPUEMU_SNAPSHOT_MAGIC   0x43455331u  /* "CES1" little-endian        */
#define CPUEMU_SNAPSHOT_VERSION 1u

#endif /* CPUEMU_COMMON_CONFIG_H */
