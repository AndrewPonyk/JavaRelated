/* =============================================================================
 *  serial.c  --  COM1 16550 UART driver
 *
 *  Standard 115200/3 = 38400 baud, 8 data bits, no parity, 1 stop bit (8N1).
 *  Initialization follows the canonical 16550 bring-up sequence and includes a
 *  loopback self-test so a dead UART is reported rather than silently hanging.
 * ===========================================================================*/
#include "../include/serial.h"
#include "../include/ports.h"

/* Register offsets from the base port. */
#define REG_DATA        0   /* DLAB=0: RX/TX buffer; DLAB=1: divisor low  */
#define REG_INT_ENABLE  1   /* DLAB=0: interrupt enable; DLAB=1: divisor high */
#define REG_FIFO_CTRL   2
#define REG_LINE_CTRL   3
#define REG_MODEM_CTRL  4
#define REG_LINE_STATUS 5

int serial_init(void) {
    outb(COM1_PORT + REG_INT_ENABLE, 0x00); /* disable interrupts */
    outb(COM1_PORT + REG_LINE_CTRL,  0x80); /* DLAB=1: set baud divisor */
    outb(COM1_PORT + REG_DATA,       0x03); /* divisor low  = 3 (38400 baud) */
    outb(COM1_PORT + REG_INT_ENABLE, 0x00); /* divisor high = 0 */
    outb(COM1_PORT + REG_LINE_CTRL,  0x03); /* DLAB=0, 8 bits, no parity, 1 stop */
    outb(COM1_PORT + REG_FIFO_CTRL,  0xC7); /* enable+clear FIFO, 14-byte threshold */
    outb(COM1_PORT + REG_MODEM_CTRL, 0x0B); /* IRQs enabled, RTS/DSR set */

    /* Loopback self-test: send 0xAE and check it echoes back. */
    outb(COM1_PORT + REG_MODEM_CTRL, 0x1E); /* loopback mode */
    outb(COM1_PORT + REG_DATA, 0xAE);
    if (inb(COM1_PORT + REG_DATA) != 0xAE) {
        return -1;                          /* UART not present/faulty */
    }

    outb(COM1_PORT + REG_MODEM_CTRL, 0x0F); /* normal operation */
    return 0;
}

static int transmit_empty(void) {
    return inb(COM1_PORT + REG_LINE_STATUS) & 0x20;  /* bit 5: THR empty */
}

void serial_putchar(char c) {
    if (c == '\n') serial_putchar('\r');    /* CRLF for terminal sanity */
    while (!transmit_empty()) { /* spin until the holding register is free */ }
    outb(COM1_PORT + REG_DATA, (u8)c);
}

void serial_write(const char *s) {
    for (u32 i = 0; s[i]; i++) serial_putchar(s[i]);
}

bool serial_received(void) {
    return (inb(COM1_PORT + REG_LINE_STATUS) & 0x01) != 0;  /* bit 0: data ready */
}

char serial_read(void) {
    while (!serial_received()) { /* block until a byte arrives */ }
    return (char)inb(COM1_PORT + REG_DATA);
}
