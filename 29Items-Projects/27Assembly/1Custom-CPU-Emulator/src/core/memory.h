/*
 * memory.h — Flat, byte-addressable guest RAM with bounds checking.
 *
 * Every access is range-checked: a guest out-of-bounds access returns
 * EMU_ERR_MEM_BOUNDS (which the CPU turns into an emulated fault) and NEVER
 * crashes the host. All multi-byte access is little-endian (x86 semantics),
 * centralized here so endianness lives in exactly one place.
 */
#ifndef CPUEMU_CORE_MEMORY_H
#define CPUEMU_CORE_MEMORY_H

#include "common/types.h"

typedef struct {
    uint8_t *bytes; /* owned buffer of `size` bytes */
    size_t   size;  /* total guest RAM in bytes     */
} memory_t;

/* Allocate `size` bytes of zeroed guest RAM. Returns EMU_ERR_NOMEM on failure. */
emu_status_t mem_init(memory_t *m, size_t size);

/* Release the backing buffer (safe to call on a zeroed/partly-init struct). */
void mem_free(memory_t *m);

/* Generic bounds-checked block copy in/out of guest memory. */
emu_status_t mem_read(const memory_t *m, uint64_t addr, void *dst, size_t n);
emu_status_t mem_write(memory_t *m, uint64_t addr, const void *src, size_t n);

/* Little-endian fixed-width helpers (width = 1/2/4/8). `out` is zero-extended. */
emu_status_t mem_read_width(const memory_t *m, uint64_t addr, uint8_t width, uint64_t *out);
emu_status_t mem_write_width(memory_t *m, uint64_t addr, uint8_t width, uint64_t value);

#endif /* CPUEMU_CORE_MEMORY_H */
