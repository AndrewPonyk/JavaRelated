; src/shellcode/utils.inc
; ---------------------------------------------------------------------------
; Shared NASM macros for shellcode development (x86-64 Linux).
;
; WARNING: macros expand inline. Naive encodings (mov rax,imm64 / mov [mem],imm32)
; introduce null bytes. Every macro below is written null-byte-free. Always
; verify with:  make extract && xxd bin/<name>.bin   (grep for 0000)
; ---------------------------------------------------------------------------
; Usage:  %include "utils.inc"
; ---------------------------------------------------------------------------

%ifndef UTILS_INC
%define UTILS_INC

; --- Linux x86-64 syscall numbers (load via `mov al, N`, never `mov eax, N`) ---
%define SYS_read    0
%define SYS_write   1
%define SYS_execve  59      ; 0x3b
%define SYS_exit    60      ; 0x3c

; ZERO reg — clear a 64-bit register without null bytes (xor, not mov 0).
%macro ZERO 1
    xor %1, %1
%endmacro

; EXIT0 — null-free exit(0). Convenience for non-payload stubs/tests.
%macro EXIT0 0
    ZERO rdi                 ; status = 0
    ZERO rax
    mov al, SYS_exit
    syscall
%endmacro

%endif ; UTILS_INC
