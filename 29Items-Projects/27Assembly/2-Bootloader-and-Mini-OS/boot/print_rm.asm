; =============================================================================
;  print_rm.asm  --  Real-mode string printing via BIOS teletype
;
;  print_string: prints a NUL-terminated string at DS:BX using
;                INT 0x10 / AH=0x0E (teletype output).
;  Clobbers nothing the caller cares about (saves/restores via pusha/popa).
; =============================================================================
[bits 16]

print_string:
    pusha
    mov     ah, 0x0E            ; BIOS teletype function
.loop:
    mov     al, [bx]            ; load next character
    cmp     al, 0               ; NUL terminator?
    je      .done
    int     0x10                ; print AL
    inc     bx
    jmp     .loop
.done:
    popa
    ret
