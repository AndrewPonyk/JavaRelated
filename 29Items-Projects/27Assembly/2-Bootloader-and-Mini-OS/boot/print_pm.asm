; =============================================================================
;  print_pm.asm  --  Protected-mode string printing via direct VGA memory
;
;  In 32-bit protected mode there is no BIOS, so we write characters straight
;  into the VGA text framebuffer at 0xB8000. Each cell is 2 bytes:
;    byte 0 = ASCII character, byte 1 = attribute (color).
;
;  print_string_pm: prints NUL-terminated string at EBX to the top-left.
; =============================================================================
[bits 32]

VIDEO_MEMORY    equ 0xB8000
WHITE_ON_BLACK  equ 0x0F        ; attribute byte: bright white on black

print_string_pm:
    pusha
    mov     edx, VIDEO_MEMORY   ; EDX = current cell address
.loop:
    mov     al, [ebx]           ; AL = character
    mov     ah, WHITE_ON_BLACK  ; AH = attribute
    cmp     al, 0               ; end of string?
    je      .done
    mov     [edx], ax           ; write char+attribute (2 bytes) to VGA
    add     ebx, 1              ; next character
    add     edx, 2              ; next VGA cell
    jmp     .loop
.done:
    popa
    ret
