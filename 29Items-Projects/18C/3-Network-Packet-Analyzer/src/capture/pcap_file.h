/* SPDX-License-Identifier: MIT
 *
 * capture/pcap_file.h — dependency-free pcap (.pcap) file reader + writer.
 *
 * Implements just enough of the classic libpcap savefile format to read and
 * write captures without linking libpcap. Used for offline replay (-r) and
 * export (-w), so the analyzer runs on hosts without libpcap/Npcap. Handles
 * both byte orders and the microsecond/nanosecond magic variants on read.
 */
#ifndef NPA_CAPTURE_PCAP_FILE_H
#define NPA_CAPTURE_PCAP_FILE_H

#include <stdio.h>

#include "common/packet.h"
#include "common/types.h"

typedef struct {
    FILE *fp;
    bool  swapped;     /* file byte order differs from host */
    bool  nanosec;     /* timestamps are nanoseconds        */
    u32   datalink;    /* DLT_* network type                */
    u32   snaplen;
} pcap_reader_t;

/* Open `path` and parse the global header. NPA_OK / NPA_ERR_IO / NPA_ERR_MALFORMED. */
npa_result_t pcap_reader_open(pcap_reader_t *r, const char *path);

/* Read the next frame into `out`. NPA_OK, NPA_ERR_AGAIN at clean EOF, else error. */
npa_result_t pcap_reader_next(pcap_reader_t *r, captured_frame_t *out);

void pcap_reader_close(pcap_reader_t *r);

typedef struct {
    FILE *fp;
    u32   datalink;
} pcap_writer_t;

/* Create `path` and write the global header (native order, microsecond ts). */
npa_result_t pcap_writer_open(pcap_writer_t *w, const char *path, u32 datalink);

/* Append one frame. */
npa_result_t pcap_writer_frame(pcap_writer_t *w, const captured_frame_t *f);

/* Append one record from raw fields (used when bytes aren't in a frame). */
npa_result_t pcap_writer_raw(pcap_writer_t *w, u64 ts_sec, u64 ts_usec,
                             u32 caplen, u32 wirelen, const u8 *bytes);

void pcap_writer_close(pcap_writer_t *w);

#endif /* NPA_CAPTURE_PCAP_FILE_H */
