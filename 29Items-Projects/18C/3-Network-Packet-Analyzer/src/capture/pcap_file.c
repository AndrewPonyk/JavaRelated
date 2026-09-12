/* SPDX-License-Identifier: MIT
 *
 * capture/pcap_file.c — pcap savefile reader/writer (no libpcap dependency).
 */
#include "capture/pcap_file.h"

#include <string.h>

#define PCAP_MAGIC_US     0xA1B2C3D4u   /* microsecond, host order            */
#define PCAP_MAGIC_US_SW  0xD4C3B2A1u   /* microsecond, swapped               */
#define PCAP_MAGIC_NS     0xA1B23C4Du   /* nanosecond, host order             */
#define PCAP_MAGIC_NS_SW  0x4D3CB2A1u   /* nanosecond, swapped                */
#define PCAP_GLOBAL_LEN   24u
#define PCAP_RECORD_LEN   16u

static u16 swap16(u16 v) { return (u16)((v >> 8) | (v << 8)); }
static u32 swap32(u32 v) {
    return ((v & 0x000000FFu) << 24) | ((v & 0x0000FF00u) << 8) |
           ((v & 0x00FF0000u) >> 8)  | ((v & 0xFF000000u) >> 24);
}

/* Read exactly n bytes; returns true on success. */
static bool read_exact(FILE *fp, void *buf, size_t n) {
    return fread(buf, 1, n, fp) == n;
}

npa_result_t pcap_reader_open(pcap_reader_t *r, const char *path) {
    if (!r || !path) return NPA_ERR_INVAL;
    memset(r, 0, sizeof *r);
    r->fp = fopen(path, "rb");
    if (!r->fp) return NPA_ERR_IO;

    u8 hdr[PCAP_GLOBAL_LEN];
    if (!read_exact(r->fp, hdr, sizeof hdr)) {
        fclose(r->fp); r->fp = NULL;
        return NPA_ERR_MALFORMED;
    }

    u32 magic;
    memcpy(&magic, hdr, 4);
    if (magic == PCAP_MAGIC_US)         { r->swapped = false; r->nanosec = false; }
    else if (magic == PCAP_MAGIC_NS)    { r->swapped = false; r->nanosec = true;  }
    else if (magic == PCAP_MAGIC_US_SW) { r->swapped = true;  r->nanosec = false; }
    else if (magic == PCAP_MAGIC_NS_SW) { r->swapped = true;  r->nanosec = true;  }
    else {
        fclose(r->fp); r->fp = NULL;
        return NPA_ERR_MALFORMED;
    }

    u32 snaplen, network;
    memcpy(&snaplen, hdr + 16, 4);
    memcpy(&network, hdr + 20, 4);
    r->snaplen  = r->swapped ? swap32(snaplen) : snaplen;
    r->datalink = r->swapped ? swap32(network) : network;
    return NPA_OK;
}

npa_result_t pcap_reader_next(pcap_reader_t *r, captured_frame_t *out) {
    if (!r || !r->fp || !out) return NPA_ERR_INVAL;

    u8 rec[PCAP_RECORD_LEN];
    size_t got = fread(rec, 1, sizeof rec, r->fp);
    if (got == 0 && feof(r->fp)) return NPA_ERR_AGAIN;   /* clean EOF */
    if (got != sizeof rec)       return NPA_ERR_MALFORMED;

    u32 ts_sec, ts_frac, incl, orig;
    memcpy(&ts_sec,  rec + 0,  4);
    memcpy(&ts_frac, rec + 4,  4);
    memcpy(&incl,    rec + 8,  4);
    memcpy(&orig,    rec + 12, 4);
    if (r->swapped) { ts_sec = swap32(ts_sec); ts_frac = swap32(ts_frac);
                      incl = swap32(incl);     orig = swap32(orig); }

    out->ts_sec   = ts_sec;
    out->ts_usec  = r->nanosec ? (ts_frac / 1000u) : ts_frac;
    out->wirelen  = orig;
    out->datalink = r->datalink;

    u32 to_read = incl;
    u32 keep = incl > NPA_MAX_FRAME_LEN ? NPA_MAX_FRAME_LEN : incl;
    if (!read_exact(r->fp, out->data, keep)) return NPA_ERR_MALFORMED;
    out->caplen = keep;

    /* Skip any bytes beyond our buffer cap so the stream stays aligned. */
    if (to_read > keep) {
        if (fseek(r->fp, (long)(to_read - keep), SEEK_CUR) != 0) return NPA_ERR_MALFORMED;
    }
    return NPA_OK;
}

void pcap_reader_close(pcap_reader_t *r) {
    if (r && r->fp) { fclose(r->fp); r->fp = NULL; }
}

/* ---- writer ------------------------------------------------------------ */

static bool write_u32(FILE *fp, u32 v) { return fwrite(&v, 4, 1, fp) == 1; }
static bool write_u16(FILE *fp, u16 v) { return fwrite(&v, 2, 1, fp) == 1; }

npa_result_t pcap_writer_open(pcap_writer_t *w, const char *path, u32 datalink) {
    if (!w || !path) return NPA_ERR_INVAL;
    w->fp = fopen(path, "wb");
    if (!w->fp) return NPA_ERR_IO;
    w->datalink = datalink;

    /* Global header: native magic, version 2.4, microsecond timestamps. */
    bool ok = write_u32(w->fp, PCAP_MAGIC_US) &&
              write_u16(w->fp, 2) && write_u16(w->fp, 4) &&
              write_u32(w->fp, 0) && write_u32(w->fp, 0) &&
              write_u32(w->fp, NPA_MAX_FRAME_LEN) &&
              write_u32(w->fp, datalink);
    if (!ok) { fclose(w->fp); w->fp = NULL; return NPA_ERR_IO; }
    return NPA_OK;
}

npa_result_t pcap_writer_raw(pcap_writer_t *w, u64 ts_sec, u64 ts_usec,
                             u32 caplen, u32 wirelen, const u8 *bytes) {
    if (!w || !w->fp || (caplen && !bytes)) return NPA_ERR_INVAL;
    bool ok = write_u32(w->fp, (u32)ts_sec) &&
              write_u32(w->fp, (u32)ts_usec) &&
              write_u32(w->fp, caplen) &&
              write_u32(w->fp, wirelen) &&
              (fwrite(bytes, 1, caplen, w->fp) == caplen);
    return ok ? NPA_OK : NPA_ERR_IO;
}

npa_result_t pcap_writer_frame(pcap_writer_t *w, const captured_frame_t *f) {
    if (!f) return NPA_ERR_INVAL;
    return pcap_writer_raw(w, f->ts_sec, f->ts_usec, f->caplen, f->wirelen, f->data);
}

void pcap_writer_close(pcap_writer_t *w) {
    if (w && w->fp) { fclose(w->fp); w->fp = NULL; }
}
