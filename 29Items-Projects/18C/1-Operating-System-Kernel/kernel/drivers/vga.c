/*
 * vga.c — VGA text-mode console driver (80x25, color).
 *
 * The framebuffer is memory-mapped at physical 0xB8000; each cell is a byte of
 * ASCII followed by an attribute byte. Marked volatile so writes aren't elided.
 */
#include "../include/kernel.h"

#define VGA_WIDTH    80
#define VGA_HEIGHT   25
#define VGA_MEM      0xB8000
#define VGA_COLOR    0x0F          /* white on black */

static volatile uint16_t *const vga = (volatile uint16_t *)VGA_MEM;
static size_t cursor_row;
static size_t cursor_col;

static inline uint16_t vga_cell(char c) { return (uint16_t)c | (VGA_COLOR << 8); }

void vga_clear(void)
{
    for (size_t i = 0; i < VGA_WIDTH * VGA_HEIGHT; i++)
        vga[i] = vga_cell(' ');
    cursor_row = cursor_col = 0;
}

static void vga_scroll(void)
{
    for (size_t r = 1; r < VGA_HEIGHT; r++)
        for (size_t c = 0; c < VGA_WIDTH; c++)
            vga[(r - 1) * VGA_WIDTH + c] = vga[r * VGA_WIDTH + c];
    for (size_t c = 0; c < VGA_WIDTH; c++)
        vga[(VGA_HEIGHT - 1) * VGA_WIDTH + c] = vga_cell(' ');
    cursor_row = VGA_HEIGHT - 1;
}

void vga_putc(char c)
{
    if (c == '\n') {
        cursor_col = 0;
        if (++cursor_row >= VGA_HEIGHT) vga_scroll();
        return;
    }
    vga[cursor_row * VGA_WIDTH + cursor_col] = vga_cell(c);
    if (++cursor_col >= VGA_WIDTH) {
        cursor_col = 0;
        if (++cursor_row >= VGA_HEIGHT) vga_scroll();
    }
}
