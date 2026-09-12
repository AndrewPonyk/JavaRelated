; =============================================================================
;  idt_load.asm  --  Execute the `lidt` instruction
;
;  void idt_load(idt_ptr_t *idt_ptr);
;  Loads the IDTR from the 6-byte limit/base structure pointed to by the
;  single stack argument (cdecl: first arg at [esp+4]).
; =============================================================================
[bits 32]
global idt_load

idt_load:
    mov     eax, [esp + 4]      ; eax = idt_ptr argument
    lidt    [eax]               ; load the IDT register
    ret

section .note.GNU-stack noalloc noexec nowrite progbits
