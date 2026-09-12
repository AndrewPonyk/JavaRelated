/*
 * memory.c — Flat guest RAM with overflow-safe bounds checking (little-endian).
 */
#include "core/memory.h"
#include "common/log.h"

#include <stdlib.h>
#include <string.h>

/* True if [addr, addr+n) lies entirely within [0, size). Overflow-safe:
 * we check `addr > size` and `n > size - addr` so a huge addr cannot wrap. */
static bool in_bounds(const memory_t *m, uint64_t addr, size_t n) {
    if (addr > m->size) {
        return false;
    }
    return (uint64_t)n <= (uint64_t)(m->size - addr);
}

emu_status_t mem_init(memory_t *m, size_t size) {
    if (m == NULL) {
        return EMU_ERR_NULL;
    }
    m->bytes = (uint8_t *)calloc(1, size ? size : 1);
    if (m->bytes == NULL) {
        m->size = 0;
        return EMU_ERR_NOMEM;
    }
    m->size = size;
    return EMU_OK;
}

void mem_free(memory_t *m) {
    if (m == NULL) {
        return;
    }
    free(m->bytes);
    m->bytes = NULL;
    m->size = 0;
}

emu_status_t mem_read(const memory_t *m, uint64_t addr, void *dst, size_t n) {
    if (m == NULL || dst == NULL) {
        return EMU_ERR_NULL;
    }
    if (!in_bounds(m, addr, n)) {
        log_trace("mem_read OOB addr=0x%llx n=%zu size=%zu",
                  (unsigned long long)addr, n, m->size);
        return EMU_ERR_MEM_BOUNDS;
    }
    memcpy(dst, m->bytes + addr, n);
    return EMU_OK;
}

emu_status_t mem_write(memory_t *m, uint64_t addr, const void *src, size_t n) {
    if (m == NULL || src == NULL) {
        return EMU_ERR_NULL;
    }
    if (!in_bounds(m, addr, n)) {
        log_trace("mem_write OOB addr=0x%llx n=%zu size=%zu",
                  (unsigned long long)addr, n, m->size);
        return EMU_ERR_MEM_BOUNDS;
    }
    memcpy(m->bytes + addr, src, n);
    return EMU_OK;
}

emu_status_t mem_read_width(const memory_t *m, uint64_t addr, uint8_t width, uint64_t *out) {
    if (out == NULL) {
        return EMU_ERR_NULL;
    }
    uint8_t buf[8] = {0};
    emu_status_t st = mem_read(m, addr, buf, width);
    if (st != EMU_OK) {
        return st;
    }
    /* Little-endian assembly into a 64-bit value. */
    uint64_t v = 0;
    for (uint8_t i = 0; i < width; ++i) {
        v |= (uint64_t)buf[i] << (8u * i);
    }
    *out = v;
    return EMU_OK;
}

emu_status_t mem_write_width(memory_t *m, uint64_t addr, uint8_t width, uint64_t value) {
    uint8_t buf[8];
    for (uint8_t i = 0; i < width; ++i) {
        buf[i] = (uint8_t)((value >> (8u * i)) & 0xFFu);
    }
    return mem_write(m, addr, buf, width);
}
