; =============================================================================
;  boot.asm  --  Stage-1 bootloader (Master Boot Record)
;
;  Lifecycle:
;    1. BIOS loads this 512-byte sector to 0x7C00 and jumps here in 16-bit
;       real mode, with the boot drive number in DL.
;    2. We print a banner, load the kernel from disk to 0x1000,
;       set up a flat GDT, switch to 32-bit protected mode, and jump to
;       the kernel entry point.
;
;  Assemble:  nasm -f bin boot/boot.asm -o build/boot.bin   (exactly 512 bytes)
; =============================================================================

[org 0x7C00]                    ; BIOS loads us here; all label offsets are
                                ; relative to this load address.

KERNEL_OFFSET   equ 0x1000      ; where we load + execute the kernel
                                ; (must match tools/linker.ld and ARCHITECTURE.md)

; -----------------------------------------------------------------------------
;  16-bit real-mode entry
; -----------------------------------------------------------------------------
[bits 16]
start:
    ; Don't trust the BIOS to have zeroed the segment registers; set up a known
    ; state so [org 0x7C00] offsets and the stack are correct. (DL is preserved
    ; across these, so we can still read the boot drive afterward.)
    cli
    xor     ax, ax
    mov     ds, ax
    mov     es, ax
    mov     ss, ax
    mov     bp, 0x9000          ; set up a real-mode stack well above us
    mov     sp, bp
    sti

    mov     [BOOT_DRIVE], dl    ; BIOS left the boot drive number in DL; save it
                                ; now that DS=0 makes this store land correctly.

    mov     bx, MSG_REAL_MODE   ; "Booting in 16-bit real mode..."
    call    print_string

    call    load_kernel         ; read the kernel image off disk → 0x1000
    call    switch_to_pm        ; never returns (jumps into 32-bit BEGIN_PM)

    jmp     $                   ; safety net; should be unreachable

; -----------------------------------------------------------------------------
;  Load the kernel from disk into memory at KERNEL_OFFSET
; -----------------------------------------------------------------------------
[bits 16]
load_kernel:
    mov     bx, MSG_LOAD_KERNEL
    call    print_string

    mov     bx, KERNEL_OFFSET   ; ES:BX = destination buffer (ES=0 here)
    mov     dh, KERNEL_SECTORS  ; how many sectors to read
    mov     dl, [BOOT_DRIVE]    ; from the drive we booted from
    call    disk_load
    ret

; -----------------------------------------------------------------------------
;  32-bit protected-mode entry (jumped to from switch_to_pm)
; -----------------------------------------------------------------------------
[bits 32]
BEGIN_PM:
    mov     ebx, MSG_PROT_MODE
    call    print_string_pm     ; prove the mode switch worked (writes to 0xB8000)

    call    KERNEL_OFFSET       ; hand control to the C kernel; does not return

    jmp     $                   ; if the kernel ever returns, halt here

; -----------------------------------------------------------------------------
;  Included routines  (order matters only for the linker, not here)
; -----------------------------------------------------------------------------
%include "boot/print_rm.asm"
%include "boot/disk_load.asm"
%include "boot/gdt.asm"
%include "boot/print_pm.asm"
%include "boot/switch_pm.asm"

; -----------------------------------------------------------------------------
;  Data
; -----------------------------------------------------------------------------
BOOT_DRIVE      db 0
KERNEL_SECTORS  equ 50          ; 25 KiB; loaded to 0x1000 (stays below the
                                ; 0x9000 real-mode stack). Keep in sync with
                                ; .env and tools/create_image.sh. If the kernel
                                ; outgrows this, switch to a stage-2 loader.

MSG_REAL_MODE   db "[boot] 16-bit real mode", 0x0D, 0x0A, 0
MSG_LOAD_KERNEL db "[boot] loading kernel from disk...", 0x0D, 0x0A, 0
MSG_PROT_MODE   db "[boot] 32-bit protected mode OK", 0

; -----------------------------------------------------------------------------
;  Boot-sector padding + signature: MUST make this file exactly 512 bytes
; -----------------------------------------------------------------------------
times 510-($-$$) db 0           ; pad with zeros up to byte 510
dw 0xAA55                       ; BIOS boot signature
