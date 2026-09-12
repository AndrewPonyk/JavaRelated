; alu_fast.asm — Optional hand-written host-side ALU helper.
;
; Demonstrates linking NASM with the C core. Built & linked only when the build
; sets CPUEMU_USE_ASM_ALU=1 (see Makefile). Linux-first: this uses the
; System V AMD64 calling convention (args in RDI, RSI, RDX; result in RAX).
; On Windows the convention differs (RCX, RDX, R8) — see docs/TECH-NOTES.md §3.6.
;
; Assemble:  nasm -f elf64 alu_fast.asm -o alu_fast.o   (Linux)
;            nasm -f win64 alu_fast.asm -o alu_fast.obj  (Windows, needs ABI edit)

BITS 64

section .text
    global alu_fast_add64

; uint64_t alu_fast_add64(uint64_t a, uint64_t b, uint64_t *rflags_out)
;   rdi = a
;   rsi = b
;   rdx = pointer to receive the host RFLAGS after the ADD
;
; Performs a true 64-bit ADD and captures the resulting flags via PUSHFQ. The
; captured CF/PF/AF/ZF/SF/OF sit at the same bit positions the C ALU uses, so the
; caller can copy them straight across for the full-width case.
alu_fast_add64:
    mov     rax, rdi        ; rax = a
    add     rax, rsi        ; rax = a + b   (sets EFLAGS)
    pushfq                  ; push 64-bit RFLAGS
    pop     r8              ; r8 = captured flags
    mov     [rdx], r8       ; *rflags_out = flags
    ret                     ; return sum in rax

; TODO: add width-aware variants (8/16/32-bit) and an alu_fast_sub64 helper so
; the optional asm path can cover more of the ALU, not just full-width ADD.
