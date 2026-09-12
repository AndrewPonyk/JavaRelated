; =============================================================================
;  gdt_flush.asm  --  Load a new GDT and reload the segment registers
;
;  void gdt_flush(gdt_ptr_t *ptr);
;  After `lgdt`, the data segment registers and CS must be reloaded so the CPU
;  picks up the new descriptors. CS can only be reloaded via a far jump.
; =============================================================================
[bits 32]
global gdt_flush

gdt_flush:
    mov     eax, [esp + 4]      ; eax = &gdt_ptr (cdecl first arg)
    lgdt    [eax]               ; load the new GDT

    mov     ax, 0x10            ; 0x10 = kernel data selector
    mov     ds, ax
    mov     es, ax
    mov     fs, ax
    mov     gs, ax
    mov     ss, ax

    jmp     0x08:.flush         ; far jump reloads CS with 0x08 (code selector)
.flush:
    ret

section .note.GNU-stack noalloc noexec nowrite progbits
