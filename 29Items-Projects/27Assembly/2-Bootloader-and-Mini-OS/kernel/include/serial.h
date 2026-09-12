/* =============================================================================
 *  serial.h  --  COM1 (16550 UART) driver
 *
 *  A serial console is the kernel's CI/observability lifeline: QEMU can route
 *  COM1 to stdout (`-serial stdio`), so tests assert on exact text without
 *  screen-scraping the VGA framebuffer (see TECH-NOTES 3.1 / 3.6).
 * ===========================================================================*/
#ifndef MINIOS_SERIAL_H
#define MINIOS_SERIAL_H

#include "types.h"

#define COM1_PORT 0x3F8

int  serial_init(void);             /* returns 0 on success, -1 if UART faulty */
void serial_putchar(char c);        /* expands '\n' -> "\r\n" */
void serial_write(const char *s);
bool serial_received(void);
char serial_read(void);             /* blocking read of one byte */

#endif /* MINIOS_SERIAL_H */
