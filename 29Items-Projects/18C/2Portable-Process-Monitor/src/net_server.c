/* net_server.c — optional WinSock2 TCP metric stream (read-only).
 *
 * A dedicated accept thread admits subscribers (optionally gated by a shared
 * token from PPMON_STREAM_TOKEN). Client sockets are non-blocking, so a slow
 * consumer is skipped for the cycle rather than stalling the sampler. The
 * stream carries no commands, so there is no input/injection surface. */
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include "ppmon/net_server.h"
#include "ppmon/csv_export.h" /* reuse the RFC-4180 escaper for stream names */
#include "ppmon/log.h"

#define MODULE "net"
#define PPMON_NET_MAX_CLIENTS 32

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <winsock2.h> /* must precede windows.h to avoid the winsock v1 clash */
#include <ws2tcpip.h>
#include <windows.h>
#include <process.h>

static volatile LONG g_wsa_refs = 0;

static ppmon_status_t wsa_acquire(void) {
    if (InterlockedIncrement(&g_wsa_refs) == 1) {
        WSADATA wsa;
        int rc = WSAStartup(MAKEWORD(2, 2), &wsa);
        if (rc != 0) {
            InterlockedDecrement(&g_wsa_refs);
            return PPMON_ERR_OS;
        }
    }
    return PPMON_OK;
}

static void wsa_release(void) {
    if (InterlockedDecrement(&g_wsa_refs) == 0) WSACleanup();
}
#endif /* _WIN32 */

struct ppmon_net {
#if defined(_WIN32)
    SOCKET listen_sock;
    HANDLE thread;
    CRITICAL_SECTION lock;
    SOCKET clients[PPMON_NET_MAX_CLIENTS];
    int nclients;
    char *frame;
    size_t frame_cap;
#endif
    volatile LONG running;
    char token[128];
    int have_token;
};

#if defined(_WIN32)
static void set_nonblocking(SOCKET s) {
    u_long mode = 1;
    ioctlsocket(s, (long)FIONBIO, &mode);
}

/* Constant-time string equality: folds any byte difference (and a length
 * mismatch) into one accumulator and always scans the candidate fully, so the
 * comparison time does not leak the secret token. */
static int ct_equal(const char *cand, const char *secret) {
    size_t lc = strlen(cand), ls = strlen(secret);
    volatile unsigned char diff = (unsigned char)((lc == ls) ? 0 : 1);
    for (size_t i = 0; i < lc; ++i) {
        unsigned char cs = (unsigned char)(i < ls ? secret[i] : 0);
        diff |= (unsigned char)((unsigned char)cand[i] ^ cs);
    }
    return diff == 0;
}

/* Read a token line (blocking, with timeout) and compare. Returns 1 if the
 * client is authorised (or no token is configured), 0 otherwise. */
static int check_token(ppmon_net_t *n, SOCKET c) {
    if (!n->have_token) return 1;
    DWORD timeout = 3000; /* ms */
    setsockopt(c, SOL_SOCKET, SO_RCVTIMEO, (const char *)&timeout, sizeof(timeout));

    char buf[160];
    int total = 0;
    while (total < (int)sizeof(buf) - 1) {
        int r = recv(c, buf + total, (int)sizeof(buf) - 1 - total, 0);
        if (r <= 0) return 0;
        total += r;
        if (memchr(buf, '\n', (size_t)total)) break;
    }
    buf[total] = '\0';
    /* Trim trailing CR/LF/space. */
    while (total > 0 &&
           (buf[total - 1] == '\n' || buf[total - 1] == '\r' || buf[total - 1] == ' ')) {
        buf[--total] = '\0';
    }
    return ct_equal(buf, n->token);
}

static unsigned __stdcall accept_thread(void *arg) {
    ppmon_net_t *n = (ppmon_net_t *)arg;
    while (n->running) {
        struct sockaddr_in addr;
        int alen = (int)sizeof(addr);
        SOCKET c = accept(n->listen_sock, (struct sockaddr *)&addr, &alen);
        if (c == INVALID_SOCKET) {
            if (!n->running) break;
            Sleep(50);
            continue;
        }
        if (!check_token(n, c)) {
            closesocket(c);
            continue;
        }
        set_nonblocking(c);

        EnterCriticalSection(&n->lock);
        if (n->nclients < PPMON_NET_MAX_CLIENTS) {
            n->clients[n->nclients++] = c;
            LOG_INFO(MODULE, "client connected (%d total)", n->nclients);
        } else {
            closesocket(c); /* at capacity */
            c = INVALID_SOCKET;
        }
        LeaveCriticalSection(&n->lock);
    }
    return 0;
}

/* Remove client at index i (caller holds the lock). */
static void drop_client(ppmon_net_t *n, int i) {
    closesocket(n->clients[i]);
    n->clients[i] = n->clients[--n->nclients];
    LOG_INFO(MODULE, "client disconnected (%d total)", n->nclients);
}
#endif /* _WIN32 */

ppmon_status_t ppmon_net_start(const char *addr, uint16_t port, ppmon_net_t **out) {
    if (!addr || !out) return PPMON_ERR_INVALID_ARG;
    ppmon_net_t *n = calloc(1, sizeof(*n));
    if (!n) return PPMON_ERR_NO_MEMORY;

    const char *tok = getenv("PPMON_STREAM_TOKEN");
    if (tok && tok[0]) {
        strncpy(n->token, tok, sizeof(n->token) - 1);
        n->have_token = 1;
    }

#if defined(_WIN32)
    ppmon_status_t st = wsa_acquire();
    if (st != PPMON_OK) {
        free(n);
        return st;
    }

    n->listen_sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (n->listen_sock == INVALID_SOCKET) {
        wsa_release();
        free(n);
        return PPMON_ERR_OS;
    }

    struct sockaddr_in sa;
    memset(&sa, 0, sizeof(sa));
    sa.sin_family = AF_INET;
    sa.sin_port   = htons(port);
    if (InetPtonA(AF_INET, addr, &sa.sin_addr) != 1) {
        LOG_ERROR(MODULE, "invalid listen address: %s", addr);
        closesocket(n->listen_sock);
        wsa_release();
        free(n);
        return PPMON_ERR_INVALID_ARG;
    }

    if (bind(n->listen_sock, (struct sockaddr *)&sa, sizeof(sa)) == SOCKET_ERROR ||
        listen(n->listen_sock, SOMAXCONN) == SOCKET_ERROR) {
        LOG_ERROR(MODULE, "bind/listen failed on %s:%u (WSA %d)", addr, port,
                  WSAGetLastError());
        closesocket(n->listen_sock);
        wsa_release();
        free(n);
        return PPMON_ERR_OS;
    }

    InitializeCriticalSection(&n->lock);
    n->running = 1;
    n->thread  = (HANDLE)_beginthreadex(NULL, 0, accept_thread, n, 0, NULL);
    if (!n->thread) {
        DeleteCriticalSection(&n->lock);
        closesocket(n->listen_sock);
        wsa_release();
        free(n);
        return PPMON_ERR_OS;
    }
    LOG_INFO(MODULE, "metric stream listening on %s:%u%s", addr, port,
             n->have_token ? " (token required)" : "");
    *out = n;
    return PPMON_OK;
#else
    (void)port;
    free(n);
    return PPMON_ERR_UNSUPPORTED;
#endif
}

void ppmon_net_stop(ppmon_net_t *n) {
    if (!n) return;
#if defined(_WIN32)
    n->running = 0;
    if (n->listen_sock != INVALID_SOCKET) closesocket(n->listen_sock); /* unblock accept() */
    if (n->thread) {
        WaitForSingleObject(n->thread, 2000);
        CloseHandle(n->thread);
    }
    EnterCriticalSection(&n->lock);
    for (int i = 0; i < n->nclients; ++i)
        closesocket(n->clients[i]);
    n->nclients = 0;
    LeaveCriticalSection(&n->lock);
    DeleteCriticalSection(&n->lock);
    free(n->frame);
    wsa_release();
#endif
    free(n);
}

ppmon_status_t ppmon_net_broadcast(ppmon_net_t *n, const ppmon_proc_metrics_t *rows,
                                   size_t count) {
    if (!n || (!rows && count)) return PPMON_ERR_INVALID_ARG;
#if defined(_WIN32)
    /* Build one line-delimited frame; reuse a growable buffer across calls. */
    size_t want = 64 + count * 160;
    if (want > n->frame_cap) {
        char *nb = realloc(n->frame, want);
        if (!nb) return PPMON_ERR_NO_MEMORY;
        n->frame     = nb;
        n->frame_cap = want;
    }

    int off = snprintf(n->frame, n->frame_cap, "FRAME %zu\n", count);
    for (size_t i = 0; i < count && off > 0 && (size_t)off < n->frame_cap; ++i) {
        const ppmon_proc_metrics_t *m = &rows[i];
        /* Escape the name so a comma/newline in a process name cannot corrupt
         * the line-delimited frame (same RFC-4180 rules as the CSV export). */
        char name_esc[PPMON_MAX_IMAGE_NAME * 2 + 4];
        size_t needed = 0;
        ppmon_csv_escape(m->image_name, name_esc, sizeof(name_esc), &needed);
        int w = snprintf(
            n->frame + off, n->frame_cap - (size_t)off, "%u,%s,%.2f,%llu,%llu,%llu,%llu\n",
            m->pid, name_esc, m->cpu_percent, (unsigned long long)m->working_set_bytes,
            (unsigned long long)m->private_bytes, (unsigned long long)m->read_bytes_per_sec,
            (unsigned long long)m->write_bytes_per_sec);
        if (w <= 0) break;
        off += w;
    }
    if (off <= 0) return PPMON_ERR_IO;
    int len = (int)((size_t)off < n->frame_cap ? off : n->frame_cap - 1);

    EnterCriticalSection(&n->lock);
    for (int i = 0; i < n->nclients;) {
        int sent = send(n->clients[i], n->frame, len, 0);
        if (sent == SOCKET_ERROR) {
            int err = WSAGetLastError();
            if (err == WSAEWOULDBLOCK) {
                ++i; /* slow consumer: drop this frame, keep the client */
            } else {
                drop_client(n, i); /* hard error: remove (do not advance i) */
            }
        } else {
            ++i;
        }
    }
    LeaveCriticalSection(&n->lock);
    return PPMON_OK;
#else
    (void)rows;
    (void)count;
    return PPMON_ERR_UNSUPPORTED;
#endif
}
