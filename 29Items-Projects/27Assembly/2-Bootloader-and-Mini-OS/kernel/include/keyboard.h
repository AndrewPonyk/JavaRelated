/* =============================================================================
 *  keyboard.h  --  PS/2 keyboard driver (IRQ1)
 *
 *  Translates raw scancodes from port 0x60 into ASCII and pushes them into a
 *  single-producer/single-consumer ring buffer that tasks drain at their
 *  leisure (see ARCHITECTURE.md steady-state data flow).
 * ===========================================================================*/
#ifndef MINIOS_KEYBOARD_H
#define MINIOS_KEYBOARD_H

#include "types.h"

#define KBD_DATA_PORT   0x60
#define KBD_BUFFER_SIZE 128     /* must be a power of two for cheap masking */

void keyboard_init(void);       /* register the IRQ1 handler */

/* Non-blocking: returns the next buffered character, or 0 if the buffer is
 * empty. Tasks poll this; the IRQ handler fills the buffer. */
char keyboard_getchar(void);

bool keyboard_has_input(void);  /* true if the ring buffer is non-empty */

#endif /* MINIOS_KEYBOARD_H */
