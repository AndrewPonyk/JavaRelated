; =============================================================================
;  gdt.asm  --  Global Descriptor Table (flat memory model)
;
;  Defines three 8-byte descriptors:
;    0x00  null descriptor      (required by the CPU)
;    0x08  code segment         base=0, limit=4GiB, ring 0, executable
;    0x10  data segment         base=0, limit=4GiB, ring 0, writable
;
;  "Flat" means both segments span the entire 4 GiB address space, so after the
;  switch we can treat memory as a single linear address space (segmentation
;  effectively disabled; paging would layer on top later).
;
;  Exports the constants CODE_SEG and DATA_SEG (selector offsets into the GDT).
; =============================================================================
[bits 16]

gdt_start:

gdt_null:                       ; mandatory null descriptor (selector 0x00)
    dd 0x00000000
    dd 0x00000000

gdt_code:                       ; code segment descriptor (selector 0x08)
    dw 0xFFFF                   ; limit (bits 0-15)
    dw 0x0000                   ; base  (bits 0-15)
    db 0x00                     ; base  (bits 16-23)
    db 10011010b                ; access: present, ring 0, code, exec/read
    db 11001111b                ; flags(4-bit gran/size) + limit (bits 16-19)
    db 0x00                     ; base  (bits 24-31)

gdt_data:                       ; data segment descriptor (selector 0x10)
    dw 0xFFFF                   ; limit (bits 0-15)
    dw 0x0000                   ; base  (bits 0-15)
    db 0x00                     ; base  (bits 16-23)
    db 10010010b                ; access: present, ring 0, data, read/write
    db 11001111b                ; flags + limit (bits 16-19)
    db 0x00                     ; base  (bits 24-31)

gdt_end:                        ; label used to compute the table size below

; --- GDT descriptor (what we feed to LGDT) -----------------------------------
gdt_descriptor:
    dw gdt_end - gdt_start - 1  ; size of GDT, minus 1 (hardware quirk)
    dd gdt_start                ; 32-bit linear base address of the GDT

; --- Selector constants (offset of each descriptor within the GDT) -----------
CODE_SEG equ gdt_code - gdt_start   ; = 0x08
DATA_SEG equ gdt_data - gdt_start   ; = 0x10
