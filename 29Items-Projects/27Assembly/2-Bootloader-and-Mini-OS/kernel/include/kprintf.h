/* =============================================================================
 *  kprintf.h  --  Minimal printf-family for the kernel
 *
 *  ksnprintf/kvsnprintf are pure (format into a caller buffer) and are
 *  unit-tested on the host. kprintf formats then emits through a sink callback
 *  the kernel installs (typically "write to VGA and serial"), so this module
 *  has no direct hardware dependency.
 *
 *  Supported: %s %c %d %i %u %x %X %p %%  with optional '0' flag and width,
 *  e.g. %08x.
 * ===========================================================================*/
#ifndef MINIOS_KPRINTF_H
#define MINIOS_KPRINTF_H

#include "types.h"
#include <stdarg.h>

int  ksnprintf(char *buf, size_t size, const char *fmt, ...);
int  kvsnprintf(char *buf, size_t size, const char *fmt, va_list ap);

/* Install the character sink kprintf writes through (VGA+serial in the kernel).
 * Until set, kprintf is a no-op (safe to call early / in host tests). */
void kprintf_set_sink(void (*sink)(char c));
int  kprintf(const char *fmt, ...);

#endif /* MINIOS_KPRINTF_H */
