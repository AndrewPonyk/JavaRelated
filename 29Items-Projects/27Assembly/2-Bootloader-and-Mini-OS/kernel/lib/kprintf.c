/* =============================================================================
 *  kprintf.c  --  Minimal printf-family implementation
 *
 *  The formatter writes into a bounded buffer (snprintf semantics: at most
 *  size-1 chars plus a NUL, returns the length that *would* have been written).
 *  kprintf() formats into a stack buffer then streams it through the installed
 *  sink. No libc, no heap.
 * ===========================================================================*/
#include "../include/kprintf.h"

/* ---- bounded output buffer ---- */
typedef struct {
    char  *buf;
    size_t size;
    size_t pos;     /* logical length (may exceed size-1; snprintf semantics) */
} outbuf_t;

static void ob_putc(outbuf_t *o, char c) {
    if (o->size && o->pos < o->size - 1) o->buf[o->pos] = c;
    o->pos++;
}

static void ob_puts(outbuf_t *o, const char *s) {
    while (*s) ob_putc(o, *s++);
}

/* Convert an unsigned value to a string (base 2..16) in `tmp`, return length. */
static u32 utoa_buf(u32 value, u32 base, bool upper, char *tmp) {
    const char *digits = upper ? "0123456789ABCDEF" : "0123456789abcdef";
    char rev[32];
    u32 i = 0;
    if (value == 0) rev[i++] = '0';
    while (value) { rev[i++] = digits[value % base]; value /= base; }
    u32 n = i;
    for (u32 j = 0; j < n; j++) tmp[j] = rev[n - 1 - j];
    tmp[n] = '\0';
    return n;
}

/* Emit `s` right-justified to `width`, padding with '0' or ' '. */
static void emit_padded(outbuf_t *o, const char *s, u32 len, u32 width,
                        bool zero_pad, bool negative) {
    u32 total = len + (negative ? 1 : 0);
    char pad = zero_pad ? '0' : ' ';

    if (zero_pad && negative) ob_putc(o, '-');     /* sign before zero padding */
    for (u32 i = total; i < width; i++) ob_putc(o, pad);
    if (!zero_pad && negative) ob_putc(o, '-');     /* sign after space padding */
    ob_puts(o, s);
}

int kvsnprintf(char *buf, size_t size, const char *fmt, va_list ap) {
    outbuf_t o = { buf, size, 0 };
    char tmp[34];

    for (u32 i = 0; fmt[i]; i++) {
        if (fmt[i] != '%') { ob_putc(&o, fmt[i]); continue; }

        i++;                                  /* consume '%' */
        bool zero_pad = false;
        if (fmt[i] == '0') { zero_pad = true; i++; }

        u32 width = 0;
        while (fmt[i] >= '0' && fmt[i] <= '9') { width = width * 10 + (u32)(fmt[i] - '0'); i++; }

        switch (fmt[i]) {
            case 's': {
                const char *s = va_arg(ap, const char *);
                if (!s) s = "(null)";
                u32 len = 0; while (s[len]) len++;
                emit_padded(&o, s, len, width, false, false);
                break;
            }
            case 'c':
                ob_putc(&o, (char)va_arg(ap, int));
                break;
            case 'd':
            case 'i': {
                i32 v = va_arg(ap, i32);
                bool neg = v < 0;
                u32 mag = neg ? (u32)(-(i64)v) : (u32)v;
                u32 len = utoa_buf(mag, 10, false, tmp);
                emit_padded(&o, tmp, len, width, zero_pad, neg);
                break;
            }
            case 'u': {
                u32 v = va_arg(ap, u32);
                u32 len = utoa_buf(v, 10, false, tmp);
                emit_padded(&o, tmp, len, width, zero_pad, false);
                break;
            }
            case 'x':
            case 'X': {
                u32 v = va_arg(ap, u32);
                u32 len = utoa_buf(v, 16, fmt[i] == 'X', tmp);
                emit_padded(&o, tmp, len, width, zero_pad, false);
                break;
            }
            case 'p': {
                u32 v = (u32)(uintptr_t)va_arg(ap, void *);
                u32 len = utoa_buf(v, 16, false, tmp);
                ob_puts(&o, "0x");
                emit_padded(&o, tmp, len, width < 8 ? 8 : width, true, false);
                break;
            }
            case '%':
                ob_putc(&o, '%');
                break;
            case '\0':
                i--;                          /* trailing '%': stop cleanly */
                break;
            default:                          /* unknown spec: print literally */
                ob_putc(&o, '%');
                ob_putc(&o, fmt[i]);
                break;
        }
    }

    if (o.size) o.buf[o.pos < o.size ? o.pos : o.size - 1] = '\0';
    return (int)o.pos;
}

int ksnprintf(char *buf, size_t size, const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    int n = kvsnprintf(buf, size, fmt, ap);
    va_end(ap);
    return n;
}

/* ---- sink-backed kprintf ---- */
static void (*g_sink)(char c) = 0;

void kprintf_set_sink(void (*sink)(char c)) { g_sink = sink; }

int kprintf(const char *fmt, ...) {
    char line[256];
    va_list ap;
    va_start(ap, fmt);
    int n = kvsnprintf(line, sizeof(line), fmt, ap);
    va_end(ap);

    if (g_sink) {
        for (int i = 0; line[i]; i++) g_sink(line[i]);
    }
    return n;
}
