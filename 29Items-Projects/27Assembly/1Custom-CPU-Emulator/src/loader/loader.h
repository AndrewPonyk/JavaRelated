/*
 * loader.h — Program loading and snapshot persistence.
 *
 * This is the emulator's "data layer". There is no SQL database; durable state
 * is a versioned machine snapshot (.ces) whose schema is:
 *
 *   [ snapshot_header ][ register_file_t (gpr[16], rip, rflags) ][ memory bytes ]
 *
 * The loader validates every field BEFORE allocating/copying (untrusted input —
 * see docs/ARCHITECTURE.md §2.5).
 */
#ifndef CPUEMU_LOADER_LOADER_H
#define CPUEMU_LOADER_LOADER_H

#include "common/types.h"
#include "core/cpu.h"

/* Load a flat binary image from `path` into guest memory at `load_addr` and set
 * RIP. Fails (EMU_ERR_IO) if the file is unreadable or larger than fits. */
emu_status_t loader_load_flat_binary(cpu_t *cpu, const char *path, uint64_t load_addr);

/* Serialize the full machine state to a versioned snapshot file. */
emu_status_t loader_save_state(const cpu_t *cpu, const char *path);

/* Restore a snapshot previously written by loader_save_state. The CPU's memory
 * size must match the snapshot's recorded size. */
emu_status_t loader_load_state(cpu_t *cpu, const char *path);

#endif /* CPUEMU_LOADER_LOADER_H */
