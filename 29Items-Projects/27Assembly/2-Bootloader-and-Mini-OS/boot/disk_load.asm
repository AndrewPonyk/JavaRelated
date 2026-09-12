; =============================================================================
;  disk_load.asm  --  Load sectors from disk using BIOS INT 0x13 / AH=0x02
;
;  Inputs:
;    DH = number of sectors to read
;    DL = drive number
;    ES:BX = destination buffer (memory address to read into)
;
;  Reads DH sectors starting at sector 2 (sector 1 is this boot sector),
;  cylinder 0, head 0. Verifies both the carry flag (error) and that the
;  number of sectors actually read matches what we asked for.
; =============================================================================
[bits 16]

disk_load:
    pusha
    push    dx                  ; stash requested sector count (DH) for later check

    mov     ah, 0x02            ; BIOS read-sectors function
    mov     al, dh              ; AL = number of sectors to read
    mov     ch, 0x00            ; cylinder 0
    mov     dh, 0x00            ; head 0
    mov     cl, 0x02            ; start at sector 2 (1-based; sector 1 = bootloader)

    int     0x13                ; BIOS disk read; DL/ES:BX already set by caller

    jc      .disk_error         ; carry flag set => read error

    pop     dx                  ; restore requested count into DH
    cmp     al, dh              ; AL = sectors actually read; must equal request
    jne     .sectors_error

    popa
    ret

.disk_error:
    mov     bx, DISK_ERR_MSG
    call    print_string
    mov     dh, ah              ; BIOS error code is in AH
    call    print_hex_byte
    jmp     .hang

.sectors_error:
    mov     bx, SECTORS_ERR_MSG
    call    print_string

.hang:
    jmp     $                   ; unrecoverable at boot: halt loudly

; --- print DH as two hex digits (tiny debug helper) --------------------------
print_hex_byte:
    pusha
    mov     cx, 2               ; two nibbles
    mov     al, dh
.next_nibble:
    rol     al, 4               ; bring high nibble into low 4 bits
    mov     bl, al
    and     bl, 0x0F
    cmp     bl, 9
    jle     .digit
    add     bl, 'A' - 10 - '0'  ; adjust for A-F
.digit:
    add     bl, '0'
    mov     ah, 0x0E
    push    ax
    mov     al, bl
    int     0x10
    pop     ax
    loop    .next_nibble
    popa
    ret

DISK_ERR_MSG    db 0x0D, 0x0A, "[boot] DISK READ ERROR 0x", 0
SECTORS_ERR_MSG db 0x0D, 0x0A, "[boot] WRONG SECTOR COUNT", 0
