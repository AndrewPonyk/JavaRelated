; =============================================================================
;  switch_pm.asm  --  Transition from 16-bit real mode to 32-bit protected mode
;
;  The canonical sequence:
;    1. cli          -- disable interrupts (real-mode IVT is about to be invalid)
;    2. lgdt         -- load our flat GDT
;    3. set CR0.PE   -- flip the Protection Enable bit
;    4. far jump     -- to a 32-bit segment to flush the prefetch queue AND
;                       load CS with the new code selector (a NEAR jump is NOT
;                       enough -- this is the #1 mode-switch bug).
;    5. reload the data segment registers and the stack, then call into C land.
; =============================================================================

[bits 16]
switch_to_pm:
    cli                         ; 1. no interrupts during the switch
    lgdt    [gdt_descriptor]    ; 2. tell the CPU where the GDT lives

    mov     eax, cr0
    or      eax, 0x1            ; 3. set CR0.PE (bit 0)
    mov     cr0, eax

    jmp     CODE_SEG:init_pm    ; 4. far jump -> flushes pipeline, loads CS

[bits 32]
init_pm:                        ; we are now executing 32-bit instructions
    mov     ax, DATA_SEG        ; 5. point every data/stack segment at the
    mov     ds, ax              ;    flat data descriptor
    mov     ss, ax
    mov     es, ax
    mov     fs, ax
    mov     gs, ax

    mov     ebp, 0x90000        ; relocate the stack high in conventional memory
    mov     esp, ebp

    call    BEGIN_PM            ; jump back to boot.asm's 32-bit continuation
