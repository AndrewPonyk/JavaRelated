/* =============================================================================
 *  vga.c  --  80x25 VGA text-mode driver
 *
 *  Writes directly into the framebuffer at 0xB8000 and drives the hardware
 *  cursor through the VGA CRT controller (ports 0x3D4/0x3D5). Handles newlines,
 *  carriage returns, backspace, and screen scrolling.
 * ===========================================================================*/
#include "../include/vga.h"
#include "../include/ports.h"

/* itoa lives in lib/; declared here to avoid a header just for two helpers. */
extern char *itoa(u32 value, char *buf, int base);

static volatile u16 *const fb = VGA_MEMORY;
static u8  cursor_col = 0;
static u8  cursor_row = 0;
static u8  color = 0x07;        /* light grey on black */

/* Build a full VGA cell (character + attribute) ready to store. */
static inline u16 vga_cell(char c, u8 attr) {
    return (u16)c | ((u16)attr << 8);
}

/* Sync the blinking hardware cursor to (cursor_col, cursor_row). */
static void vga_move_cursor(void) {
    u16 pos = (u16)(cursor_row * VGA_WIDTH + cursor_col);
    outb(0x3D4, 0x0F);          /* low cursor location register  */
    outb(0x3D5, (u8)(pos & 0xFF));
    outb(0x3D4, 0x0E);          /* high cursor location register */
    outb(0x3D5, (u8)((pos >> 8) & 0xFF));
}

/* Scroll the screen up by one line when we run off the bottom. */
static void vga_scroll(void) {
    if (cursor_row < VGA_HEIGHT) return;

    for (u32 i = 0; i < (VGA_HEIGHT - 1) * VGA_WIDTH; i++) {
        fb[i] = fb[i + VGA_WIDTH];               /* move every row up one */
    }
    u16 blank = vga_cell(' ', color);            /* clear the last line   */
    for (u32 i = (VGA_HEIGHT - 1) * VGA_WIDTH; i < VGA_HEIGHT * VGA_WIDTH; i++) {
        fb[i] = blank;
    }
    cursor_row = VGA_HEIGHT - 1;
}

void vga_set_color(vga_color fg, vga_color bg) {
    color = vga_attr(fg, bg);
}

void vga_clear(void) {
    u16 blank = vga_cell(' ', color);
    for (u32 i = 0; i < VGA_WIDTH * VGA_HEIGHT; i++) fb[i] = blank;
    cursor_col = 0;
    cursor_row = 0;
    vga_move_cursor();
}

void vga_init(void) {
    color = vga_attr(VGA_LIGHT_GREY, VGA_BLACK);
    vga_clear();
}

void vga_putchar(char c) {
    switch (c) {
        case '\n':
            cursor_col = 0;
            cursor_row++;
            break;
        case '\r':
            cursor_col = 0;
            break;
        case '\b':                              /* backspace */
            if (cursor_col > 0) {
                cursor_col--;
                fb[cursor_row * VGA_WIDTH + cursor_col] = vga_cell(' ', color);
            }
            break;
        case '\t':
            cursor_col = (u8)((cursor_col + 4) & ~3u);
            break;
        default:
            fb[cursor_row * VGA_WIDTH + cursor_col] = vga_cell(c, color);
            cursor_col++;
            break;
    }

    if (cursor_col >= VGA_WIDTH) {              /* wrap at right edge */
        cursor_col = 0;
        cursor_row++;
    }
    vga_scroll();
    vga_move_cursor();
}

void vga_print(const char *str) {
    for (size_t i = 0; str[i] != '\0'; i++) vga_putchar(str[i]);
}

void vga_print_at(const char *str, u8 col, u8 row) {
    cursor_col = col;
    cursor_row = row;
    vga_print(str);
}

void vga_putc_at(char c, u8 col, u8 row, u8 attr) {
    if (col >= VGA_WIDTH || row >= VGA_HEIGHT) return;
    fb[row * VGA_WIDTH + col] = vga_cell(c, attr);
}

void vga_print_dec(u32 value) {
    char buf[12];
    vga_print(itoa(value, buf, 10));
}

void vga_print_hex(u32 value) {
    char buf[12];
    vga_print("0x");
    vga_print(itoa(value, buf, 16));
}
