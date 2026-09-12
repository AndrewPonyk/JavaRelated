/* SPDX-License-Identifier: MIT
 *
 * capture/capture.c — packet acquisition.
 *
 * Two interchangeable backends behind one interface:
 *   - NPA_HAVE_PCAP defined  → libpcap (Linux) / Npcap (Windows): live + offline
 *                              + BPF + live re-filter + capture-to-disk.
 *   - NPA_HAVE_PCAP undefined → built-in offline backend (pcap_file reader);
 *                              live capture reports "needs Npcap". Lets the
 *                              analyzer run on Windows with zero external libs.
 *
 * capture_write_pcap() is portable (uses pcap_file's writer) in both builds.
 */
#include "capture/capture.h"

#include "capture/pcap_file.h"
#include "util/log.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* Unify the Linux Makefile's HAVE_LIBPCAP with the cross-platform name. */
#if defined(HAVE_LIBPCAP) && !defined(NPA_HAVE_PCAP)
#  define NPA_HAVE_PCAP 1
#endif

#if defined(NPA_HAVE_PCAP)
#  include <pcap.h>
#endif

#define CAP_FILTER_MAX 1024
#define CAP_BATCH        64

/* ---- Portable pcap export (used by the UI 'w' key, both builds) -------- */

npa_result_t capture_write_pcap(const char *path, u32 datalink,
                                const pcap_record_t *recs, size_t n) {
    if (!path || (n > 0 && !recs)) return NPA_ERR_INVAL;
    pcap_writer_t w;
    npa_result_t r = pcap_writer_open(&w, path, datalink);
    if (r != NPA_OK) return r;
    for (size_t i = 0; i < n; ++i) {
        r = pcap_writer_raw(&w, recs[i].ts_sec, recs[i].ts_usec,
                            recs[i].caplen, recs[i].wirelen, recs[i].bytes);
        if (r != NPA_OK) break;
    }
    pcap_writer_close(&w);
    return r;
}

/* ======================================================================== */
#if defined(NPA_HAVE_PCAP)
/* ---- libpcap / Npcap backend ------------------------------------------ */

struct capture_ctx {
    const npa_config_t *cfg;
    ring_buffer_t      *ring;
    pthread_t           thread;
    atomic_bool         started;
    bool                joined;
    atomic_bool         running;
    atomic_bool         finished;
    atomic_bool         stop;
    bool                is_offline;
    pcap_t             *pcap;
    pcap_dumper_t      *dumper;
    pthread_mutex_t     filt_lock;
    char                pending_filter[CAP_FILTER_MAX];
    atomic_bool         filter_dirty;
};

static void on_packet(u_char *user, const struct pcap_pkthdr *h, const u_char *bytes) {
    struct capture_ctx *ctx = (struct capture_ctx *)user;
    if (ctx->dumper) pcap_dump((u_char *)ctx->dumper, h, bytes);

    captured_frame_t f;
    u32 caplen = (u32)h->caplen;
    if (caplen > NPA_MAX_FRAME_LEN) caplen = NPA_MAX_FRAME_LEN;
    f.ts_sec   = (u64)h->ts.tv_sec;
    f.ts_usec  = (u64)h->ts.tv_usec;
    f.caplen   = caplen;
    f.wirelen  = (u32)h->len;
    f.datalink = (u32)pcap_datalink(ctx->pcap);
    memcpy(f.data, bytes, caplen);

    npa_result_t r = (ctx->cfg->ring_policy == RING_FULL_BLOCK)
                         ? ring_push(ctx->ring, &f, true)
                         : ring_push_drop(ctx->ring, &f);
    if (r == NPA_ERR_AGAIN) pcap_breakloop(ctx->pcap);
}

static npa_result_t open_source(struct capture_ctx *ctx, char *errbuf) {
    const npa_config_t *cfg = ctx->cfg;
    if (cfg->pcap_file) {
        ctx->is_offline = true;
        ctx->pcap = pcap_open_offline(cfg->pcap_file, errbuf);
    } else {
        ctx->pcap = pcap_open_live(cfg->interface, cfg->snaplen,
                                   cfg->promiscuous ? 1 : 0, 100, errbuf);
    }
    return ctx->pcap ? NPA_OK : NPA_ERR_IO;
}

static npa_result_t compile_and_set(struct capture_ctx *ctx, const char *bpf, char *errbuf) {
    struct bpf_program prog;
    bpf_u_int32 net = 0, mask = 0;
    if (ctx->cfg->interface) pcap_lookupnet(ctx->cfg->interface, &net, &mask, errbuf);
    if (pcap_compile(ctx->pcap, &prog, bpf, 1, mask) != 0) {
        snprintf(errbuf, PCAP_ERRBUF_SIZE, "bpf compile: %s", pcap_geterr(ctx->pcap));
        return NPA_ERR_INVAL;
    }
    int rc = pcap_setfilter(ctx->pcap, &prog);
    pcap_freecode(&prog);
    if (rc != 0) {
        snprintf(errbuf, PCAP_ERRBUF_SIZE, "setfilter: %s", pcap_geterr(ctx->pcap));
        return NPA_ERR_IO;
    }
    return NPA_OK;
}

static void apply_pending_filter(struct capture_ctx *ctx) {
    if (!atomic_load(&ctx->filter_dirty)) return;
    char bpf[CAP_FILTER_MAX];
    pthread_mutex_lock(&ctx->filt_lock);
    snprintf(bpf, sizeof bpf, "%s", ctx->pending_filter);
    atomic_store(&ctx->filter_dirty, false);
    pthread_mutex_unlock(&ctx->filt_lock);
    char errbuf[PCAP_ERRBUF_SIZE] = {0};
    if (compile_and_set(ctx, bpf, errbuf) != NPA_OK)
        LOG_W("capture: live filter '%s' rejected: %s", bpf, errbuf);
    else
        LOG_I("capture: applied live filter '%s'", bpf);
}

static void *capture_thread_main(void *arg) {
    struct capture_ctx *ctx = arg;
    LOG_I("capture: loop started");
    while (!atomic_load(&ctx->stop)) {
        apply_pending_filter(ctx);
        int n = pcap_dispatch(ctx->pcap, CAP_BATCH, on_packet, (u_char *)ctx);
        if (n == -2) break;
        if (n == -1) { LOG_E("capture: pcap_dispatch: %s", pcap_geterr(ctx->pcap)); break; }
        if (n == 0 && ctx->is_offline) {
            atomic_store(&ctx->finished, true);
            LOG_I("capture: source EOF");
            break;
        }
    }
    if (ctx->dumper) pcap_dump_flush(ctx->dumper);
    ring_close(ctx->ring);
    atomic_store(&ctx->running, false);
    return NULL;
}

npa_result_t capture_create(capture_ctx_t **out, const npa_config_t *cfg, ring_buffer_t *ring) {
    if (!out || !cfg || !ring) return NPA_ERR_INVAL;
    struct capture_ctx *ctx = calloc(1, sizeof *ctx);
    if (!ctx) return NPA_ERR_NOMEM;
    ctx->cfg = cfg; ctx->ring = ring; ctx->joined = false; ctx->is_offline = false;
    atomic_init(&ctx->started, false);
    atomic_init(&ctx->running, false);
    atomic_init(&ctx->finished, false);
    atomic_init(&ctx->stop, false);
    pthread_mutex_init(&ctx->filt_lock, NULL);
    atomic_init(&ctx->filter_dirty, false);

    char errbuf[PCAP_ERRBUF_SIZE] = {0};
    npa_result_t r = open_source(ctx, errbuf);
    if (r != NPA_OK) {
        LOG_F("capture: cannot open source: %s", errbuf);
        pthread_mutex_destroy(&ctx->filt_lock); free(ctx); return r;
    }
    if (cfg->bpf_filter && *cfg->bpf_filter) {
        r = compile_and_set(ctx, cfg->bpf_filter, errbuf);
        if (r != NPA_OK) {
            LOG_F("capture: %s", errbuf);
            pcap_close(ctx->pcap); pthread_mutex_destroy(&ctx->filt_lock); free(ctx); return r;
        }
    }
    if (cfg->write_file) {
        ctx->dumper = pcap_dump_open(ctx->pcap, cfg->write_file);
        if (!ctx->dumper) LOG_W("capture: cannot open --write '%s': %s", cfg->write_file, pcap_geterr(ctx->pcap));
        else LOG_I("capture: teeing packets to %s", cfg->write_file);
    }
    LOG_I("capture: opened %s (datalink=%d)",
          cfg->pcap_file ? cfg->pcap_file : cfg->interface, pcap_datalink(ctx->pcap));
    *out = ctx;
    return NPA_OK;
}

npa_result_t capture_set_filter(capture_ctx_t *ctx, const char *bpf) {
    if (!ctx || !bpf) return NPA_ERR_INVAL;
    pthread_mutex_lock(&ctx->filt_lock);
    snprintf(ctx->pending_filter, sizeof ctx->pending_filter, "%s", bpf);
    pthread_mutex_unlock(&ctx->filt_lock);
    atomic_store(&ctx->filter_dirty, true);
    return NPA_OK;
}

void capture_destroy(capture_ctx_t *ctx) {
    if (!ctx) return;
    if (ctx->dumper) pcap_dump_close(ctx->dumper);
    if (ctx->pcap)   pcap_close(ctx->pcap);
    pthread_mutex_destroy(&ctx->filt_lock);
    free(ctx);
}

/* ======================================================================== */
#else  /* !NPA_HAVE_PCAP — built-in offline backend (no libpcap/Npcap) ----- */

struct capture_ctx {
    const npa_config_t *cfg;
    ring_buffer_t      *ring;
    pthread_t           thread;
    atomic_bool         started;
    bool                joined;
    atomic_bool         running;
    atomic_bool         finished;
    atomic_bool         stop;
    pcap_reader_t       reader;
    bool                have_reader;
    pcap_writer_t       writer;
    bool                have_writer;
};

static void *capture_thread_main(void *arg) {
    struct capture_ctx *ctx = arg;
    captured_frame_t *f = malloc(sizeof *f);   /* 64 KiB: off the stack */
    if (!f) {
        LOG_F("capture: OOM allocating frame");
        ring_close(ctx->ring);
        atomic_store(&ctx->running, false);
        return NULL;
    }
    LOG_I("capture: replaying %s", ctx->cfg->pcap_file);
    while (!atomic_load(&ctx->stop)) {
        npa_result_t r = pcap_reader_next(&ctx->reader, f);
        if (r == NPA_ERR_AGAIN) { atomic_store(&ctx->finished, true); LOG_I("capture: source EOF"); break; }
        if (r != NPA_OK) { LOG_E("capture: read error in %s", ctx->cfg->pcap_file); break; }

        if (ctx->have_writer) pcap_writer_frame(&ctx->writer, f);

        npa_result_t pr = (ctx->cfg->ring_policy == RING_FULL_BLOCK)
                              ? ring_push(ctx->ring, f, true)
                              : ring_push_drop(ctx->ring, f);
        if (pr == NPA_ERR_AGAIN) break;   /* ring closed during shutdown */
    }
    free(f);
    ring_close(ctx->ring);
    atomic_store(&ctx->running, false);
    return NULL;
}

npa_result_t capture_create(capture_ctx_t **out, const npa_config_t *cfg, ring_buffer_t *ring) {
    if (!out || !cfg || !ring) return NPA_ERR_INVAL;
    struct capture_ctx *ctx = calloc(1, sizeof *ctx);
    if (!ctx) return NPA_ERR_NOMEM;
    ctx->cfg = cfg; ctx->ring = ring; ctx->joined = false;
    atomic_init(&ctx->started, false);
    atomic_init(&ctx->running, false);
    atomic_init(&ctx->finished, false);
    atomic_init(&ctx->stop, false);

    if (!cfg->pcap_file) {
        LOG_F("capture: live capture on '%s' requires Npcap — rebuild with "
              "NPA_HAVE_PCAP and link wpcap (offline -r works without it)",
              cfg->interface ? cfg->interface : "?");
        free(ctx);
        return NPA_ERR_UNSUPPORTED;
    }

    npa_result_t r = pcap_reader_open(&ctx->reader, cfg->pcap_file);
    if (r != NPA_OK) {
        LOG_F("capture: cannot open pcap '%s'", cfg->pcap_file);
        free(ctx);
        return r;
    }
    ctx->have_reader = true;

    if (cfg->bpf_filter && *cfg->bpf_filter)
        LOG_W("capture: BPF filter ignored (built-in reader; needs Npcap)");

    if (cfg->write_file) {
        if (pcap_writer_open(&ctx->writer, cfg->write_file, ctx->reader.datalink) == NPA_OK)
            ctx->have_writer = true;
        else
            LOG_W("capture: cannot open --write '%s'", cfg->write_file);
    }

    LOG_I("capture: opened %s (datalink=%u, built-in reader)",
          cfg->pcap_file, ctx->reader.datalink);
    *out = ctx;
    return NPA_OK;
}

npa_result_t capture_set_filter(capture_ctx_t *ctx, const char *bpf) {
    (void)ctx; (void)bpf;
    LOG_W("capture: live BPF re-filter requires Npcap");
    return NPA_ERR_UNSUPPORTED;
}

void capture_destroy(capture_ctx_t *ctx) {
    if (!ctx) return;
    if (ctx->have_writer) pcap_writer_close(&ctx->writer);
    if (ctx->have_reader) pcap_reader_close(&ctx->reader);
    free(ctx);
}

#endif /* NPA_HAVE_PCAP */

/* ---- Shared lifecycle (identical for both backends) -------------------- */

npa_result_t capture_start(capture_ctx_t *ctx) {
    if (!ctx) return NPA_ERR_INVAL;
    atomic_store(&ctx->running, true);
    if (pthread_create(&ctx->thread, NULL, capture_thread_main, ctx) != 0) {
        atomic_store(&ctx->running, false);
        return NPA_ERR_INTERNAL;
    }
    atomic_store(&ctx->started, true);
    return NPA_OK;
}

void capture_stop(capture_ctx_t *ctx) {
    if (!ctx) return;
    atomic_store(&ctx->stop, true);
#if defined(NPA_HAVE_PCAP)
    if (atomic_load(&ctx->started) && !ctx->joined) {
        pcap_breakloop(ctx->pcap);
        pthread_join(ctx->thread, NULL);
        ctx->joined = true;
    }
#else
    if (atomic_load(&ctx->started) && !ctx->joined) {
        pthread_join(ctx->thread, NULL);
        ctx->joined = true;
    }
#endif
}

bool capture_finished(const capture_ctx_t *ctx) {
    return ctx && atomic_load((atomic_bool *)&ctx->finished);
}
