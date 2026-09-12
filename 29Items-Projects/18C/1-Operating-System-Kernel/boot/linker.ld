/*
 * linker.ld — Kernel link script (x86-64, higher-half).
 *
 * .boot (Multiboot2 header + 32-bit bring-up + early page tables) is linked at
 * the LOW physical load address (1 MiB) so it is directly addressable in 32-bit
 * mode. The rest of the kernel is linked at the higher-half virtual base, with
 * AT() placing its physical load address right after .boot. GRUB loads by the
 * physical (LMA) addresses; the CPU runs at the virtual (VMA) addresses once
 * paging is on.
 */

ENTRY(_start)

KERNEL_VMA = 0xFFFFFFFF80000000;
KERNEL_LMA = 0x100000;            /* 1 MiB */

SECTIONS
{
    . = KERNEL_LMA;

    /* Low-mapped boot section: header must be in the first 32 KiB for GRUB. */
    .boot : {
        KEEP(*(.multiboot_header))
        *(.boot)
    }

    /* Everything below runs in the higher half. */
    . += KERNEL_VMA;

    .text ALIGN(4K) : AT(ADDR(.text) - KERNEL_VMA) {
        *(.text .text.*)
    }

    .rodata ALIGN(4K) : AT(ADDR(.rodata) - KERNEL_VMA) {
        *(.rodata .rodata.*)
    }

    .data ALIGN(4K) : AT(ADDR(.data) - KERNEL_VMA) {
        *(.data .data.*)
    }

    .bss ALIGN(4K) : AT(ADDR(.bss) - KERNEL_VMA) {
        *(COMMON)
        *(.bss .bss.*)
    }

    kernel_end = .;

    /DISCARD/ : { *(.comment) *(.note .note.*) *(.eh_frame) }
}
