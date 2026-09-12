/*
 * pit.c — 8253/8254 Programmable Interval Timer.
 *
 * Programs channel 0 to fire IRQ0 at a fixed frequency. Each tick drives the
 * scheduler's time accounting (feature aging for the ML priority model) and the
 * global uptime counter.
 */
#include "../../include/kernel.h"
#include "../../include/sched.h"
#include "io.h"

#define PIT_CH0    0x40
#define PIT_CMD    0x43
#define PIT_FREQ   1193182u        /* base oscillator frequency, Hz */

static volatile uint64_t g_ticks;
static uint32_t          g_hz;

void pit_init(uint32_t hz)
{
    g_hz = hz;
    uint32_t divisor = PIT_FREQ / hz;

    outb(PIT_CMD, 0x36);                       /* ch0, lo/hi, mode 3 (square) */
    outb(PIT_CH0, (uint8_t)(divisor & 0xFF));
    outb(PIT_CH0, (uint8_t)((divisor >> 8) & 0xFF));
    KLOG_DEBUG("pit: timer at %u Hz", hz);
}

/* Called from the IRQ0 handler (idt.c). */
void timer_on_tick(void)
{
    g_ticks++;
    sched_tick();          /* time accounting; cooperative scheduler won't switch here */
}

uint64_t timer_ticks(void) { return g_ticks; }
uint32_t timer_hz(void)    { return g_hz; }
