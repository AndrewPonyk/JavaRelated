/* SPDX-License-Identifier: MIT
 *
 * capture/capture.h — libpcap acquisition layer.
 *
 * Owns the pcap handle and runs the capture loop on its own thread, copying
 * each frame into the ring buffer. Supports both live (-i) and offline (-r)
 * sources; the rest of the pipeline can't tell the difference.
 */
#ifndef NPA_CAPTURE_CAPTURE_H
#define NPA_CAPTURE_CAPTURE_H

#include <pthread.h>
#include <stdatomic.h>

#include "buffer/ring_buffer.h"
#include "common/types.h"
#include "util/config.h"

typedef struct capture_ctx capture_ctx_t;   /* opaque; defined in capture.c */

/*
 * Create a capture context bound to cfg's source (interface or pcap_file),
 * compile+install the BPF filter, and target `ring` for output. Does NOT start
 * the thread yet. On error returns non-OK and writes a message via the logger.
 */
npa_result_t capture_create(capture_ctx_t **out, const npa_config_t *cfg,
                            ring_buffer_t *ring);

/* Spawn the capture thread; it runs until capture_stop() or EOF (file mode). */
npa_result_t capture_start(capture_ctx_t *ctx);

/*
 * Ask the capture loop to stop (pcap_breakloop + flag) and join the thread.
 * Idempotent. After this returns, no more frames will be enqueued.
 */
void capture_stop(capture_ctx_t *ctx);

/* Free the context and close the pcap handle. Call after capture_stop(). */
void capture_destroy(capture_ctx_t *ctx);

/* True once the capture source reached EOF (file mode) — UI can note "done". */
bool capture_finished(const capture_ctx_t *ctx);

/*
 * Recompile and install a new BPF filter live, without restarting. The request
 * is queued and applied by the capture thread itself (libpcap handles are not
 * safe to reconfigure from another thread). Returns NPA_OK once queued.
 */
npa_result_t capture_set_filter(capture_ctx_t *ctx, const char *bpf);

/* One record for capture_write_pcap(). bytes points to caplen bytes. */
typedef struct {
    u64        ts_sec;
    u64        ts_usec;
    u32        caplen;
    u32        wirelen;
    const u8  *bytes;
} pcap_record_t;

/*
 * Write `n` records to a pcap file at `path` with the given DLT_* datalink.
 * Used by the UI 'w' export. Standalone (uses pcap_open_dead) — needs no live
 * capture. Returns NPA_ERR_UNSUPPORTED if built without libpcap.
 */
npa_result_t capture_write_pcap(const char *path, u32 datalink,
                                const pcap_record_t *recs, size_t n);

#endif /* NPA_CAPTURE_CAPTURE_H */
