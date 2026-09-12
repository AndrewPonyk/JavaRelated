/* =============================================================================
 *  timer.h  --  8254 Programmable Interval Timer (PIT), channel 0
 *
 *  Configures the PIT to fire IRQ0 at a fixed frequency. Each tick is the
 *  heartbeat that drives preemptive scheduling.
 * ===========================================================================*/
#ifndef MINIOS_TIMER_H
#define MINIOS_TIMER_H

#include "types.h"

#define PIT_CHANNEL0    0x40
#define PIT_COMMAND     0x43
#define PIT_BASE_FREQ   1193182 /* input clock to the PIT, in Hz */

void timer_init(u32 frequency_hz);  /* program channel 0; e.g. 100 -> 10ms tick */
u32  timer_ticks(void);             /* monotonically increasing tick count */

#endif /* MINIOS_TIMER_H */
