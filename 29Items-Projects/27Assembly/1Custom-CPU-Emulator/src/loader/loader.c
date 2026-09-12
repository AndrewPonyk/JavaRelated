/*
 * loader.c — Flat-binary loader + versioned snapshot save/restore.
 */
#include "loader/loader.h"
#include "common/config.h"
#include "common/log.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* On-disk snapshot header. Written/read field-by-field via the helpers below to
 * avoid struct-padding portability issues. */
typedef struct {
    uint32_t magic;     /* CPUEMU_SNAPSHOT_MAGIC   */
    uint32_t version;   /* CPUEMU_SNAPSHOT_VERSION */
    uint32_t model;     /* cpu_model_t             */
    uint64_t mem_size;  /* guest RAM size in bytes */
    uint64_t instret;   /* instructions retired    */
    uint64_t cycles;    /* accumulated cycles      */
} snapshot_header_t;

/* ---- little-endian field IO ---- */
static bool wr_u32(FILE *f, uint32_t v) {
    uint8_t b[4]; for (int i = 0; i < 4; ++i) b[i] = (uint8_t)(v >> (8 * i));
    return fwrite(b, 1, 4, f) == 4;
}
static bool wr_u64(FILE *f, uint64_t v) {
    uint8_t b[8]; for (int i = 0; i < 8; ++i) b[i] = (uint8_t)(v >> (8 * i));
    return fwrite(b, 1, 8, f) == 8;
}
static bool rd_u32(FILE *f, uint32_t *v) {
    uint8_t b[4]; if (fread(b, 1, 4, f) != 4) return false;
    *v = 0; for (int i = 0; i < 4; ++i) *v |= (uint32_t)b[i] << (8 * i);
    return true;
}
static bool rd_u64(FILE *f, uint64_t *v) {
    uint8_t b[8]; if (fread(b, 1, 8, f) != 8) return false;
    *v = 0; for (int i = 0; i < 8; ++i) *v |= (uint64_t)b[i] << (8 * i);
    return true;
}

emu_status_t loader_load_flat_binary(cpu_t *cpu, const char *path, uint64_t load_addr) {
    if (cpu == NULL || path == NULL) {
        return EMU_ERR_NULL;
    }
    FILE *f = fopen(path, "rb");
    if (f == NULL) {
        log_error("cannot open '%s'", path);
        return EMU_ERR_IO;
    }

    if (fseek(f, 0, SEEK_END) != 0) { fclose(f); return EMU_ERR_IO; }
    long sz = ftell(f);
    if (sz < 0) { fclose(f); return EMU_ERR_IO; }
    rewind(f);

    /* Validate size BEFORE allocating (untrusted input). */
    if (load_addr > cpu->mem.size || (uint64_t)sz > cpu->mem.size - load_addr) {
        log_error("image (%ld bytes) does not fit at 0x%llx in %zu-byte RAM",
                  sz, (unsigned long long)load_addr, cpu->mem.size);
        fclose(f);
        return EMU_ERR_IO;
    }

    uint8_t *tmp = (uint8_t *)malloc((size_t)sz ? (size_t)sz : 1);
    if (tmp == NULL) { fclose(f); return EMU_ERR_NOMEM; }
    if (fread(tmp, 1, (size_t)sz, f) != (size_t)sz) {
        free(tmp); fclose(f); return EMU_ERR_IO;
    }
    fclose(f);

    emu_status_t st = cpu_load_program(cpu, tmp, (size_t)sz, load_addr);
    free(tmp);
    return st;
}

emu_status_t loader_save_state(const cpu_t *cpu, const char *path) {
    if (cpu == NULL || path == NULL) {
        return EMU_ERR_NULL;
    }
    FILE *f = fopen(path, "wb");
    if (f == NULL) {
        log_error("cannot create snapshot '%s'", path);
        return EMU_ERR_IO;
    }

    bool ok = true;
    ok = ok && wr_u32(f, CPUEMU_SNAPSHOT_MAGIC);
    ok = ok && wr_u32(f, CPUEMU_SNAPSHOT_VERSION);
    ok = ok && wr_u32(f, (uint32_t)cpu->model);
    ok = ok && wr_u64(f, cpu->mem.size);
    ok = ok && wr_u64(f, cpu->instret);
    ok = ok && wr_u64(f, cpu->timing.total_cycles);

    for (int i = 0; ok && i < REG_COUNT; ++i) {
        ok = wr_u64(f, cpu->regs.gpr[i]);
    }
    ok = ok && wr_u64(f, cpu->regs.rip);
    ok = ok && wr_u64(f, cpu->regs.rflags);

    if (ok && cpu->mem.size > 0) {
        ok = fwrite(cpu->mem.bytes, 1, cpu->mem.size, f) == cpu->mem.size;
    }

    fclose(f);
    if (!ok) {
        log_error("write error saving snapshot '%s'", path);
        return EMU_ERR_IO;
    }
    log_info("snapshot saved to '%s'", path);
    return EMU_OK;
}

emu_status_t loader_load_state(cpu_t *cpu, const char *path) {
    if (cpu == NULL || path == NULL) {
        return EMU_ERR_NULL;
    }
    FILE *f = fopen(path, "rb");
    if (f == NULL) {
        log_error("cannot open snapshot '%s'", path);
        return EMU_ERR_IO;
    }

    snapshot_header_t h;
    bool ok = rd_u32(f, &h.magic) && rd_u32(f, &h.version) && rd_u32(f, &h.model)
           && rd_u64(f, &h.mem_size) && rd_u64(f, &h.instret) && rd_u64(f, &h.cycles);
    if (!ok || h.magic != CPUEMU_SNAPSHOT_MAGIC) {
        log_error("'%s' is not a valid snapshot", path);
        fclose(f);
        return EMU_ERR_IO;
    }
    if (h.version != CPUEMU_SNAPSHOT_VERSION) {
        log_error("snapshot version %u unsupported (expected %u)",
                  h.version, CPUEMU_SNAPSHOT_VERSION);
        fclose(f);
        return EMU_ERR_IO;
    }
    if (h.mem_size != cpu->mem.size) {
        log_error("snapshot RAM size %llu != current %zu",
                  (unsigned long long)h.mem_size, cpu->mem.size);
        fclose(f);
        return EMU_ERR_IO;
    }

    /* Read everything into temporaries first; commit to the live CPU only after
     * the whole snapshot has been read cleanly. A truncated/corrupt file can
     * then never leave the machine half-restored and inconsistent. */
    register_file_t tmp_regs;
    memset(&tmp_regs, 0, sizeof tmp_regs);
    for (int i = 0; ok && i < REG_COUNT; ++i) {
        ok = rd_u64(f, &tmp_regs.gpr[i]);
    }
    ok = ok && rd_u64(f, &tmp_regs.rip);
    ok = ok && rd_u64(f, &tmp_regs.rflags);

    uint8_t *tmp_mem = NULL;
    if (ok && h.mem_size > 0) {
        tmp_mem = (uint8_t *)malloc(cpu->mem.size);
        if (tmp_mem == NULL) {
            fclose(f);
            return EMU_ERR_NOMEM;
        }
        ok = fread(tmp_mem, 1, cpu->mem.size, f) == cpu->mem.size;
    }
    fclose(f);
    if (!ok) {
        free(tmp_mem);
        log_error("truncated snapshot '%s'", path);
        return EMU_ERR_IO;
    }

    /* Atomic commit — nothing above this point mutated the live CPU. */
    cpu->regs = tmp_regs;
    if (tmp_mem != NULL) {
        memcpy(cpu->mem.bytes, tmp_mem, cpu->mem.size);
        free(tmp_mem);
    }
    cpu->model = (cpu_model_t)h.model;
    cpu->instret = h.instret;
    timing_init(&cpu->timing, cpu->model);
    cpu->timing.total_cycles = h.cycles;
    cpu->halted = false;
    log_info("snapshot restored from '%s'", path);
    return EMU_OK;
}
