/* status.c — ppmon_status_t -> string. Pure, fully implemented. */
#include "ppmon/ppmon.h"

const char *ppmon_status_str(ppmon_status_t status) {
    switch (status) {
    case PPMON_OK:
        return "OK";
    case PPMON_ERR_INVALID_ARG:
        return "invalid argument";
    case PPMON_ERR_NO_MEMORY:
        return "out of memory";
    case PPMON_ERR_ACCESS_DENIED:
        return "access denied";
    case PPMON_ERR_NOT_FOUND:
        return "not found";
    case PPMON_ERR_OS:
        return "OS error";
    case PPMON_ERR_IO:
        return "I/O error";
    case PPMON_ERR_AGAIN:
        return "transient, retry";
    case PPMON_ERR_UNSUPPORTED:
        return "unsupported";
    default:
        return "unknown status";
    }
}
