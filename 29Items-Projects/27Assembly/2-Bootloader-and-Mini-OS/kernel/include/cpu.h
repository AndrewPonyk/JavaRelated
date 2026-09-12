/* =============================================================================
 *  cpu.h  --  Small inline CPU control helpers
 *
 *  Wraps privileged instructions so the rest of the kernel reads cleanly. The
 *  freestanding (kernel) build emits the real instructions; a hosted build
 *  (the unit tests) compiles them to no-ops, which lets host tests link code
 *  that contains these calls without choking on inline assembly.
 * ===========================================================================*/
#ifndef MINIOS_CPU_H
#define MINIOS_CPU_H

#if defined(__STDC_HOSTED__) && (__STDC_HOSTED__ == 1)

/* Host test build: privileged instructions are meaningless -> no-ops. */
static inline void cpu_halt(void)  {}
static inline void cpu_cli(void)   {}
static inline void cpu_sti(void)   {}
static inline void cpu_relax(void) {}

#else  /* freestanding kernel build */

static inline void cpu_halt(void)  { __asm__ volatile ("hlt"); }
static inline void cpu_cli(void)   { __asm__ volatile ("cli"); }
static inline void cpu_sti(void)   { __asm__ volatile ("sti"); }
static inline void cpu_relax(void) { __asm__ volatile ("pause"); }

#endif

#endif /* MINIOS_CPU_H */
