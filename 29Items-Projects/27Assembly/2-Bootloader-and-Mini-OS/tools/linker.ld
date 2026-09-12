/* =============================================================================
 *  linker.ld  --  Link script for the Mini-OS kernel
 *
 *  The bootloader loads the kernel to physical 0x1000 and jumps there blindly,
 *  so two things are non-negotiable:
 *    1. The image is linked to run at 0x1000 (`. = 0x1000`).
 *    2. kernel_entry.o's code is FIRST, so _start sits exactly at 0x1000.
 *
 *  We emit ELF (for symbols/debugging); the Makefile then objcopy's it to a
 *  flat binary that gets concatenated after boot.bin.
 * ===========================================================================*/
ENTRY(_start)

SECTIONS
{
    . = 0x1000;                     /* kernel load + execution address */

    .text : ALIGN(4)
    {
        *kernel_entry.o(.text)      /* MUST be first: _start at 0x1000 */
        *(.text)
        *(.text.*)
    }

    .rodata : ALIGN(4)
    {
        *(.rodata)
        *(.rodata.*)
    }

    .data : ALIGN(4)
    {
        *(.data)
        *(.data.*)
    }

    .bss : ALIGN(4)
    {
        __bss_start = .;
        *(COMMON)
        *(.bss)
        *(.bss.*)
        __bss_end = .;
    }

    /* Discard host-toolchain metadata that has no meaning bare-metal and can
     * otherwise break a fixed-address link (see TECH-NOTES pitfalls). */
    /DISCARD/ :
    {
        *(.comment)
        *(.eh_frame)
        *(.note .note.*)
    }

    _kernel_end = .;
}
