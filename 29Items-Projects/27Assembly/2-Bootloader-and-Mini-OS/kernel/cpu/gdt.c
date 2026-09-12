/* =============================================================================
 *  gdt.c  --  Kernel-owned flat Global Descriptor Table
 *
 *  Three descriptors matching the bootloader's layout so cached segment
 *  registers stay valid: null, ring-0 code (0x08), ring-0 data (0x10), each
 *  spanning the full 4 GiB. Living in kernel BSS, this GDT is safe from the
 *  boot-sector-clobber problem that motivated it (see gdt.h).
 * ===========================================================================*/
#include "../include/gdt.h"
#include "../include/types.h"

typedef struct {
    u16 limit_low;     /* limit bits 0-15  */
    u16 base_low;      /* base  bits 0-15  */
    u8  base_mid;      /* base  bits 16-23 */
    u8  access;        /* present | DPL | type */
    u8  granularity;   /* flags | limit bits 16-19 */
    u8  base_high;     /* base  bits 24-31 */
} __attribute__((packed)) gdt_entry_t;

typedef struct {
    u16 limit;
    u32 base;
} __attribute__((packed)) gdt_ptr_t;

static gdt_entry_t gdt[3];
static gdt_ptr_t   gdt_ptr;

/* Defined in gdt_flush.asm: lgdt + reload data segments + far-jump to reload CS. */
extern void gdt_flush(gdt_ptr_t *ptr);

static void gdt_set_entry(int n, u32 base, u32 limit, u8 access, u8 gran) {
    gdt[n].base_low    = (u16)(base & 0xFFFF);
    gdt[n].base_mid    = (u8)((base >> 16) & 0xFF);
    gdt[n].base_high   = (u8)((base >> 24) & 0xFF);
    gdt[n].limit_low   = (u16)(limit & 0xFFFF);
    gdt[n].granularity = (u8)(((limit >> 16) & 0x0F) | (gran & 0xF0));
    gdt[n].access      = access;
}

void gdt_init(void) {
    gdt_ptr.limit = (u16)(sizeof(gdt) - 1);
    gdt_ptr.base  = (u32)(uintptr_t)&gdt;

    gdt_set_entry(0, 0, 0x00000, 0x00, 0x00);  /* null descriptor          */
    gdt_set_entry(1, 0, 0xFFFFF, 0x9A, 0xCF);  /* 0x08 code: P,ring0,exec/r */
    gdt_set_entry(2, 0, 0xFFFFF, 0x92, 0xCF);  /* 0x10 data: P,ring0,r/w    */

    gdt_flush(&gdt_ptr);
}
