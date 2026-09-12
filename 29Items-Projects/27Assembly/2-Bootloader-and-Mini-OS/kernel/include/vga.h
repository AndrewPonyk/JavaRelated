/* =============================================================================
 *  vga.h  --  80x25 VGA text-mode screen driver
 *
 *  The framebuffer lives at 0xB8000; each of the 80*25 cells is two bytes:
 *  an ASCII character and a color attribute. This is the kernel's primary
 *  output device ("the frontend").
 * ===========================================================================*/
#ifndef MINIOS_VGA_H
#define MINIOS_VGA_H

#include "types.h"

#define VGA_WIDTH   80
#define VGA_HEIGHT  25
#define VGA_MEMORY  ((volatile u16 *)0xB8000)

/* Standard CGA/VGA 4-bit color palette. */
typedef enum {
    VGA_BLACK = 0, VGA_BLUE, VGA_GREEN, VGA_CYAN, VGA_RED, VGA_MAGENTA,
    VGA_BROWN, VGA_LIGHT_GREY, VGA_DARK_GREY, VGA_LIGHT_BLUE, VGA_LIGHT_GREEN,
    VGA_LIGHT_CYAN, VGA_LIGHT_RED, VGA_LIGHT_MAGENTA, VGA_YELLOW, VGA_WHITE
} vga_color;

/* Pack a foreground/background pair into the attribute byte. */
static inline u8 vga_attr(vga_color fg, vga_color bg) {
    return (u8)(fg | (bg << 4));
}

void vga_init(void);                    /* clear screen, reset cursor + color */
void vga_clear(void);
void vga_set_color(vga_color fg, vga_color bg);
void vga_putchar(char c);               /* handles \n, \r, \b, and scrolling   */
void vga_print(const char *str);
void vga_print_at(const char *str, u8 col, u8 row);

/* Write a single cell directly, WITHOUT moving the logical cursor or touching
 * the current color -- used by background tasks for a fixed-position heartbeat
 * so they don't fight the shell for the cursor. */
void vga_putc_at(char c, u8 col, u8 row, u8 attr);

/* Convenience: print an unsigned integer in decimal or hex (uses lib/itoa). */
void vga_print_dec(u32 value);
void vga_print_hex(u32 value);

#endif /* MINIOS_VGA_H */
