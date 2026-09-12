; =============================================================================
;  kernel_entry.asm  --  32-bit kernel entry trampoline
;
;  CRITICAL: this object file MUST be linked FIRST so that it sits at the very
;  start of the kernel image (load address 0x1000). The bootloader does a blind
;  `call 0x1000`, so whatever lands at 0x1000 is executed -- it has to be this
;  stub, not some arbitrary C function the linker happened to place first.
;
;  Before entering C we zero the .bss section. .bss is NOBITS (not stored in the
;  flat binary), so the loader doesn't bring it in; C statics that are implicitly
;  zero (e.g. interrupt_handlers[] = {0}) would otherwise hold disk/RAM garbage.
;
;  See tools/linker.ld (ENTRY + .text ordering + __bss_start/__bss_end) and the
;  Makefile OBJ order.
; =============================================================================
[bits 32]

[extern kernel_main]            ; defined in kernel/kernel.c
[extern __bss_start]            ; provided by tools/linker.ld
[extern __bss_end]

global _start
_start:
    ; --- zero .bss: for (edi = __bss_start; edi < __bss_end; ) *edi++ = 0 ---
    mov     edi, __bss_start
    mov     ecx, __bss_end
    sub     ecx, edi            ; ecx = byte count
    xor     eax, eax
    rep     stosb               ; store AL (0) ECX times from [EDI]

    call    kernel_main         ; enter C; kernel_main() should never return
    cli                         ; but if it does, halt the machine cleanly
.hang:
    hlt
    jmp     .hang

; Declare a non-executable stack so the linker doesn't assume an executable one.
section .note.GNU-stack noalloc noexec nowrite progbits
