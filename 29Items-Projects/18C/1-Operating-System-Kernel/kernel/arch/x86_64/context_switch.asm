; context_switch.asm — Kernel-thread context switch + new-thread trampoline.
;
;   void context_switch(uint64_t *save_rsp, uint64_t new_rsp);
;     rdi = where to store the outgoing thread's stack pointer
;     rsi = the incoming thread's stack pointer
;
; Callee-saved registers live on each thread's own kernel stack; switching is
; just "push callee-saved, swap rsp, pop callee-saved, ret". A freshly created
; thread's stack is primed (in process.c) so the final `ret` lands in
; thread_trampoline with the entry point in r15.

bits 64
section .text
global context_switch
global thread_trampoline
extern sched_exit

context_switch:
    push rbx
    push rbp
    push r12
    push r13
    push r14
    push r15

    mov [rdi], rsp          ; save outgoing rsp
    mov rsp, rsi            ; load incoming rsp

    pop r15
    pop r14
    pop r13
    pop r12
    pop rbp
    pop rbx
    ret

; Entry shim for brand-new threads. r15 = real entry point (set up on the
; primed stack). Enable interrupts, run the thread, and if it ever returns,
; exit cleanly instead of falling off the stack.
thread_trampoline:
    sti
    call r15
    xor edi, edi
    call sched_exit
.hang:
    hlt
    jmp .hang

; Mark the stack as non-executable (silences the linker's GNU-stack warning).
section .note.GNU-stack noalloc noexec nowrite progbits
