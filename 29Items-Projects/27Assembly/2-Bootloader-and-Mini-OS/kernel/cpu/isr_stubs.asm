; =============================================================================
;  isr_stubs.asm  --  Assembly entry stubs for CPU exceptions and hardware IRQs
;
;  The CPU pushes EIP/CS/EFLAGS (and sometimes an error code) on an interrupt.
;  Each stub normalizes the frame -- pushing a dummy error code where the CPU
;  doesn't, then the vector number -- and jumps to a common handler that saves
;  the general registers (`pusha`), sets up the kernel data segment, and calls
;  into C (isr_handler / irq_handler). On return it restores everything and
;  `iret`s back to the interrupted code.
; =============================================================================
[bits 32]

[extern isr_handler]
[extern irq_handler]

; --- exceptions that DO push an error code: 8, 10, 11, 12, 13, 14 -----------
%macro ISR_ERRCODE 1
global isr%1
isr%1:
    cli
    push    dword %1            ; push interrupt (vector) number
    jmp     isr_common_stub
%endmacro

; --- exceptions that DON'T: push a dummy 0 so the frame layout is uniform ----
%macro ISR_NOERRCODE 1
global isr%1
isr%1:
    cli
    push    dword 0            ; dummy error code
    push    dword %1            ; vector number
    jmp     isr_common_stub
%endmacro

; --- hardware IRQs: 1st arg = IRQ number, 2nd = remapped vector (0x20+) -------
%macro IRQ 2
global irq%1
irq%1:
    cli
    push    dword 0            ; dummy error code
    push    dword %2            ; vector number (32..47)
    jmp     irq_common_stub
%endmacro

; CPU exceptions 0..31
ISR_NOERRCODE 0
ISR_NOERRCODE 1
ISR_NOERRCODE 2
ISR_NOERRCODE 3
ISR_NOERRCODE 4
ISR_NOERRCODE 5
ISR_NOERRCODE 6
ISR_NOERRCODE 7
ISR_ERRCODE   8
ISR_NOERRCODE 9
ISR_ERRCODE   10
ISR_ERRCODE   11
ISR_ERRCODE   12
ISR_ERRCODE   13
ISR_ERRCODE   14
ISR_NOERRCODE 15
ISR_NOERRCODE 16
ISR_NOERRCODE 17
ISR_NOERRCODE 18
ISR_NOERRCODE 19
ISR_NOERRCODE 20
ISR_NOERRCODE 21
ISR_NOERRCODE 22
ISR_NOERRCODE 23
ISR_NOERRCODE 24
ISR_NOERRCODE 25
ISR_NOERRCODE 26
ISR_NOERRCODE 27
ISR_NOERRCODE 28
ISR_NOERRCODE 29
ISR_NOERRCODE 30
ISR_NOERRCODE 31

; Hardware IRQs 0..15 -> vectors 0x20..0x2F
IRQ 0,  32
IRQ 1,  33
IRQ 2,  34
IRQ 3,  35
IRQ 4,  36
IRQ 5,  37
IRQ 6,  38
IRQ 7,  39
IRQ 8,  40
IRQ 9,  41
IRQ 10, 42
IRQ 11, 43
IRQ 12, 44
IRQ 13, 45
IRQ 14, 46
IRQ 15, 47

; --- shared tail: save state, call C, switch stack, restore state, iret -------
;
;  The C handler RETURNS (in EAX) the kernel stack pointer to resume on. Usually
;  that's the same frame we passed in, so this behaves like an ordinary IRET.
;  But the scheduler may return a *different* task's saved frame -- `mov esp,eax`
;  then makes the subsequent pops/iret restore that task instead. This single
;  trick is the whole of preemptive context switching.
;
;  We do NOT `sti` before `iret`: each task frame carries EFLAGS with IF set, so
;  IRET restores the interrupt flag atomically. Re-enabling earlier would risk a
;  nested interrupt while we are mid-switch on a half-restored stack.
%macro COMMON_STUB 1
    pusha                       ; edi,esi,ebp,esp,ebx,edx,ecx,eax
    mov     ax, ds
    push    eax                 ; save the data segment selector

    mov     ax, 0x10            ; load the kernel data segment (DATA_SEG)
    mov     ds, ax
    mov     es, ax
    mov     fs, ax
    mov     gs, ax

    push    esp                 ; pass pointer to registers_t as the argument
    call    %1
    mov     esp, eax            ; switch to the stack/frame the handler chose

    pop     eax                 ; restore the (possibly new task's) data segment
    mov     ds, ax
    mov     es, ax
    mov     fs, ax
    mov     gs, ax

    popa
    add     esp, 8              ; pop the pushed error code + vector number
    iret                        ; restores EIP/CS/EFLAGS (and re-enables IF)
%endmacro

isr_common_stub:
    COMMON_STUB isr_handler

irq_common_stub:
    COMMON_STUB irq_handler

section .note.GNU-stack noalloc noexec nowrite progbits
