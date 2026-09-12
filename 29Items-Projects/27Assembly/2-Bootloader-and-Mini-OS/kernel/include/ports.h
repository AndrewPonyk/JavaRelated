/* =============================================================================
 *  ports.h  --  x86 port-mapped I/O primitives
 *
 *  Thin wrappers around the IN/OUT instructions. Keeping these behind a header
 *  lets host unit tests link a mock implementation (see TECH-NOTES 3.2).
 * ===========================================================================*/
#ifndef MINIOS_PORTS_H
#define MINIOS_PORTS_H

#include "types.h"

u8   inb(u16 port);             /* read  one byte  from a port */
void outb(u16 port, u8 value);  /* write one byte  to   a port */
u16  inw(u16 port);             /* read  one word  from a port */
void outw(u16 port, u16 value); /* write one word  to   a port */

/* Short delay by writing to an unused port (0x80); used to give slow PIC/PIT
 * hardware time to settle between consecutive commands. */
void io_wait(void);

#endif /* MINIOS_PORTS_H */
