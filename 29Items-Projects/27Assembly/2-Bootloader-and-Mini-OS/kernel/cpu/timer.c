/* =============================================================================
 *  timer.c  --  8254 PIT channel-0 driver; the scheduler's heartbeat
 *
 *  Programs channel 0 to fire IRQ0 at `frequency_hz`. Each tick bumps a
 *  monotonic counter and invokes the scheduler so tasks are preempted on a
 *  fixed quantum.
 * ===========================================================================*/
#include "../include/timer.h"
#include "../include/ports.h"
#include "../include/isr.h"
#include "../include/scheduler.h"

static volatile u32 ticks = 0;
static u32 quantum_ticks = 1;       /* preempt every N ticks (set in init) */

static void timer_callback(registers_t *regs) {
    (void)regs;
    ticks++;

    /* Request preemption every quantum. The actual context switch happens when
     * irq_handler returns (scheduler_on_interrupt_return), not here -- doing it
     * at IRQ-return time is what lets us swap kernel stacks safely. */
    if (ticks % quantum_ticks == 0) {
        scheduler_request_resched();
    }
}

void timer_init(u32 frequency_hz) {
    if (frequency_hz == 0) frequency_hz = 100;

    /* Divisor = base frequency / desired frequency. */
    u32 divisor = PIT_BASE_FREQ / frequency_hz;

    /* ~10ms quantum regardless of tick rate (at least 1 tick). */
    quantum_ticks = frequency_hz / 100;
    if (quantum_ticks == 0) quantum_ticks = 1;

    /* Command byte: channel 0, lobyte/hibyte access, mode 3 (square wave). */
    outb(PIT_COMMAND, 0x36);
    outb(PIT_CHANNEL0, (u8)(divisor & 0xFF));        /* low byte  */
    outb(PIT_CHANNEL0, (u8)((divisor >> 8) & 0xFF)); /* high byte */

    register_interrupt_handler(IRQ0, timer_callback);
}

u32 timer_ticks(void) {
    return ticks;
}
