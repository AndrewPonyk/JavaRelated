/* =============================================================================
 *  gdt.h  --  Global Descriptor Table (kernel-owned)
 *
 *  The bootloader's GDT lives in the boot sector at ~0x7C00, which the kernel's
 *  BSS grows over and clobbers. So the kernel installs its OWN flat GDT in
 *  kernel memory early in boot; otherwise the first hardware interrupt faults
 *  when the CPU re-reads a corrupt GDT to reload CS. Must run before `sti`.
 * ===========================================================================*/
#ifndef MINIOS_GDT_H
#define MINIOS_GDT_H

void gdt_init(void);    /* build a flat GDT (0x08 code, 0x10 data) and load it */

#endif /* MINIOS_GDT_H */
