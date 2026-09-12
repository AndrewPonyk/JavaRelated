/* =============================================================================
 *  keyboard.c  --  PS/2 keyboard driver (IRQ1)
 *
 *  On each IRQ1 we read one scancode from port 0x60, translate "make" codes to
 *  ASCII (tracking shift + caps-lock state), and enqueue the character into a
 *  lock-free single-producer/single-consumer ring buffer. Tasks drain it via
 *  keyboard_getchar(). "Break" codes (bit 7 set) are key releases.
 *
 *  Scancode set 1, US QWERTY. Non-printable keys (F1-F12, arrows, keypad, etc.)
 *  map to 0 and are ignored by the buffer.
 * ===========================================================================*/
#include "../include/keyboard.h"
#include "../include/ports.h"
#include "../include/isr.h"

/* Unshifted map. Index = scancode (0x00..0x39 cover the main block). */
static const char scancode_ascii[128] = {
    0,   27,  '1', '2', '3', '4', '5', '6', '7', '8',   /* 0x00-0x09 */
    '9', '0', '-', '=', '\b','\t','q', 'w', 'e', 'r',   /* 0x0A-0x13 */
    't', 'y', 'u', 'i', 'o', 'p', '[', ']', '\n',0,     /* 0x14-0x1D (0x1D=LCtrl) */
    'a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', ';',   /* 0x1E-0x27 */
    '\'','`', 0,  '\\','z', 'x', 'c', 'v', 'b', 'n',     /* 0x28-0x31 (0x2A=LShift) */
    'm', ',', '.', '/', 0,  '*', 0,  ' ', 0,  0,        /* 0x32-0x3B (0x39=Space) */
    /* 0x3C..0x7F: F-keys, keypad, locks -> non-printable (0) */
};

/* Shifted map (Shift held or relevant Caps-Lock case). */
static const char scancode_ascii_shift[128] = {
    0,   27,  '!', '@', '#', '$', '%', '^', '&', '*',   /* 0x00-0x09 */
    '(', ')', '_', '+', '\b','\t','Q', 'W', 'E', 'R',   /* 0x0A-0x13 */
    'T', 'Y', 'U', 'I', 'O', 'P', '{', '}', '\n',0,     /* 0x14-0x1D */
    'A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L', ':',   /* 0x1E-0x27 */
    '"', '~', 0,  '|', 'Z', 'X', 'C', 'V', 'B', 'N',     /* 0x28-0x31 */
    'M', '<', '>', '?', 0,  '*', 0,  ' ', 0,  0,        /* 0x32-0x3B */
};

/* Scancode constants (set 1). */
#define SC_LSHIFT_MAKE  0x2A
#define SC_RSHIFT_MAKE  0x36
#define SC_LSHIFT_BREAK 0xAA
#define SC_RSHIFT_BREAK 0xB6
#define SC_CAPS_MAKE    0x3A

/* --- ring buffer (size is a power of two for the cheap mask) --- */
static volatile char buffer[KBD_BUFFER_SIZE];
static volatile u32  head = 0;  /* producer (IRQ) writes here */
static volatile u32  tail = 0;  /* consumer (task) reads here */
static bool shift_down = false;
static bool caps_lock  = false;

static void buffer_push(char c) {
    u32 next = (head + 1) & (KBD_BUFFER_SIZE - 1);
    if (next == tail) return;   /* full: drop the keystroke rather than corrupt */
    buffer[head] = c;
    head = next;
}

/* Is this an alphabetic make-code (so Caps-Lock affects it)? */
static bool is_alpha(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
}

/* The IRQ1 callback, registered with the interrupt dispatcher. */
static void keyboard_callback(registers_t *regs) {
    (void)regs;
    u8 scancode = inb(KBD_DATA_PORT);   /* MUST read 0x60 or no more IRQs fire */

    switch (scancode) {
        case SC_LSHIFT_MAKE:
        case SC_RSHIFT_MAKE:  shift_down = true;  return;
        case SC_LSHIFT_BREAK:
        case SC_RSHIFT_BREAK: shift_down = false; return;
        case SC_CAPS_MAKE:    caps_lock = !caps_lock; return;
        default: break;
    }

    if (scancode & 0x80) return;        /* other key releases: ignore */

    char base    = scancode_ascii[scancode];
    char shifted = scancode_ascii_shift[scancode];

    /* Caps-Lock XORs the shift state, but only for letters. */
    bool use_shift = shift_down;
    if (caps_lock && is_alpha(base)) use_shift = !use_shift;

    char c = use_shift ? shifted : base;
    if (c) buffer_push(c);
}

void keyboard_init(void) {
    register_interrupt_handler(IRQ1, keyboard_callback);
}

bool keyboard_has_input(void) {
    return head != tail;
}

char keyboard_getchar(void) {
    if (head == tail) return 0;         /* empty */
    char c = buffer[tail];
    tail = (tail + 1) & (KBD_BUFFER_SIZE - 1);
    return c;
}
