/*
 * net_server.h — Optional WinSock2 TCP metric stream (read-only).
 *
 * Binds to 127.0.0.1 by default. Accepts subscribers and pushes line-delimited
 * metric records; it consumes NO input that affects execution, so there is no
 * command/injection surface. Runs on its own thread behind a bounded queue.
 */
#ifndef PPMON_NET_SERVER_H
#define PPMON_NET_SERVER_H

#include "ppmon/ppmon.h"
#include "ppmon/metrics.h"

typedef struct ppmon_net ppmon_net_t;

/*
 * Start the server thread bound to addr:port.
 * TODO: WSAStartup (refcounted), socket/bind/listen, non-blocking accept loop
 *       with select(); optional shared-token handshake before streaming.
 */
ppmon_status_t ppmon_net_start(const char *addr, uint16_t port, ppmon_net_t **out);

/* Stop the server thread and run WSACleanup if this was the last user. */
void ppmon_net_stop(ppmon_net_t *n);

/*
 * Enqueue one cycle's metrics for broadcast to all subscribers.
 * Non-blocking; drops oldest on overflow so the sampler is never stalled.
 */
ppmon_status_t ppmon_net_broadcast(ppmon_net_t *n, const ppmon_proc_metrics_t *rows,
                                   size_t count);

#endif /* PPMON_NET_SERVER_H */
