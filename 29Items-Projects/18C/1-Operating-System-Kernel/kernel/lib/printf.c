/*
 * printf.c — Minimal kprintf + klog + panic.
 *
 * Fans output out to BOTH the VGA console (human) and serial (CI/logs), the one
 * logging path described in ARCHITECTURE.md §2.6. Supports a small subset of
 * format specifiers: %c %s %d %u %x %p %%.
 */
#include "../include/kernel.h"
#include <stdarg.h>

/* Driver sinks */
void vga_putc(char c);
void serial_putc(char c);

static void emit(char c)
{
    vga_putc(c);
    if (c == '\n') serial_putc('\r');
    serial_putc(c);
}

static void emit_str(const char *s)
{
    if (!s) s = "(null)";           /* tolerate %s with a NULL argument */
    for (; *s; s++) emit(*s);
}

static void emit_uint(uint64_t v, int base, bool upper)
{
    const char *digits = upper ? "0123456789ABCDEF" : "0123456789abcdef";
    char tmp[24];
    int  i = 0;
    if (v == 0) { emit('0'); return; }
    while (v && i < (int)sizeof(tmp)) { tmp[i++] = digits[v % base]; v /= base; }
    while (i--) emit(tmp[i]);
}

static void emit_int(int64_t v)
{
    if (v < 0) { emit('-'); emit_uint((uint64_t)(-v), 10, false); }
    else         emit_uint((uint64_t)v, 10, false);
}

static int kvprintf(const char *fmt, va_list ap)
{
    for (; *fmt; fmt++) {
        if (*fmt != '%') { emit(*fmt); continue; }
        fmt++;
        /* swallow an optional 'l' length modifier (we are LP64). */
        if (*fmt == 'l') fmt++;
        switch (*fmt) {
            case 'c': emit((char)va_arg(ap, int)); break;
            case 's': emit_str(va_arg(ap, const char *)); break;
            case 'd': emit_int(va_arg(ap, long)); break;
            case 'u': emit_uint(va_arg(ap, unsigned long), 10, false); break;
            case 'x': emit_uint(va_arg(ap, unsigned long), 16, false); break;
            case 'p': emit_str("0x");
                      emit_uint((uint64_t)va_arg(ap, void *), 16, false); break;
            case '%': emit('%'); break;
            default:  emit('%'); emit(*fmt); break;
        }
    }
    return 0;
}

int kprintf(const char *fmt, ...)
{
    va_list ap;
    va_start(ap, fmt);
    int r = kvprintf(fmt, ap);
    va_end(ap);
    return r;
}

static const char *level_tag(log_level_t l)
{
    switch (l) {
        case LOG_DEBUG: return "[DEBUG] ";
        case LOG_INFO:  return "[INFO ] ";
        case LOG_WARN:  return "[WARN ] ";
        case LOG_ERROR: return "[ERROR] ";
        default:        return "[?????] ";
    }
}

void klog(log_level_t level, const char *fmt, ...)
{
    emit_str(level_tag(level));
    va_list ap;
    va_start(ap, fmt);
    kvprintf(fmt, ap);
    va_end(ap);
    emit('\n');
}

__attribute__((noreturn))
void panic(const char *fmt, ...)
{
    emit_str("\n*** KERNEL PANIC: ");
    va_list ap;
    va_start(ap, fmt);
    kvprintf(fmt, ap);
    va_end(ap);
    emit_str(" ***\n");
    /* Halt with interrupts disabled; the frozen machine can be inspected with
     * `make debug` (QEMU GDB stub) for a full register snapshot. */
    for (;;) __asm__ volatile("cli; hlt");
}
