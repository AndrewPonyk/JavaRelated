; isr.asm — Exception & IRQ entry stubs (NASM, x86-64).
;
; Each stub pushes a (possibly dummy) error code and the vector number, then
; jumps to isr_common, which saves all GPRs, calls the C `isr_handler(regs*)`,
; restores, and `iretq`s. The exported `isr_stub_table` lets idt.c install all
; 48 gates (0-31 exceptions, 32-47 IRQs) in a loop.

bits 64
section .text
extern isr_handler

; Vectors that push their own error code (per the Intel SDM).
%macro ISR_ERR 1
global isr%1
isr%1:
    push %1                 ; int_no (error code already on stack)
    jmp isr_common
%endmacro

; Vectors with no CPU error code — push a dummy 0 to keep the frame uniform.
%macro ISR_NOERR 1
global isr%1
isr%1:
    push 0                  ; dummy error code
    push %1                 ; int_no
    jmp isr_common
%endmacro

; IRQs (remapped to 32..47): never push an error code.
%macro IRQ 1
global isr%1
isr%1:
    push 0
    push %1
    jmp isr_common
%endmacro

; --- CPU exceptions 0..31 ---
ISR_NOERR 0
ISR_NOERR 1
ISR_NOERR 2
ISR_NOERR 3
ISR_NOERR 4
ISR_NOERR 5
ISR_NOERR 6
ISR_NOERR 7
ISR_ERR   8
ISR_NOERR 9
ISR_ERR   10
ISR_ERR   11
ISR_ERR   12
ISR_ERR   13
ISR_ERR   14
ISR_NOERR 15
ISR_NOERR 16
ISR_ERR   17
ISR_NOERR 18
ISR_NOERR 19
ISR_NOERR 20
ISR_NOERR 21
ISR_NOERR 22
ISR_NOERR 23
ISR_NOERR 24
ISR_NOERR 25
ISR_NOERR 26
ISR_NOERR 27
ISR_NOERR 28
ISR_NOERR 29
ISR_NOERR 30
ISR_NOERR 31

; --- Hardware IRQs 32..47 ---
IRQ 32
IRQ 33
IRQ 34
IRQ 35
IRQ 36
IRQ 37
IRQ 38
IRQ 39
IRQ 40
IRQ 41
IRQ 42
IRQ 43
IRQ 44
IRQ 45
IRQ 46
IRQ 47

; Generic stub for any other vector (48..255).
global isr_unhandled
isr_unhandled:
    push 0
    push 255
    jmp isr_common

; --- Common handler: save state, call C, restore, return ---
isr_common:
    push rax
    push rbx
    push rcx
    push rdx
    push rsi
    push rdi
    push rbp
    push r8
    push r9
    push r10
    push r11
    push r12
    push r13
    push r14
    push r15

    mov rdi, rsp            ; arg0 = pointer to saved register frame
    cld
    call isr_handler

    pop r15
    pop r14
    pop r13
    pop r12
    pop r11
    pop r10
    pop r9
    pop r8
    pop rbp
    pop rdi
    pop rsi
    pop rdx
    pop rcx
    pop rbx
    pop rax

    add rsp, 16            ; discard int_no + error code
    iretq

; --- Table of stub addresses for idt.c (vectors 0..47) ---
section .rodata
global isr_stub_table
isr_stub_table:
%assign i 0
%rep 48
    dq isr %+ i
%assign i i+1
%endrep

; Mark the stack as non-executable (silences the linker's GNU-stack warning).
section .note.GNU-stack noalloc noexec nowrite progbits
