; interrupt_demo.asm — demonstrates a software interrupt (INT n).
; Assemble:  nasm -f bin interrupt_demo.asm -o interrupt_demo.bin
; Run:       cpuemu --program interrupt_demo.bin --headless
;
; The emulator's IDT is owned by the HOST: a handler for a vector is installed
; via idt_set_handler() before running (e.g. a syscall-style vector 0x80). If no
; handler is registered, this program instead exercises the *unhandled-interrupt*
; fault path — the emulator reports the fault and halts cleanly rather than
; crashing the host (see docs/ARCHITECTURE.md §2.6). Both outcomes are useful to
; observe in the visualizer.

bits 64
org 0x1000

_start:
    mov eax, 1          ; arg in eax (syscall-style convention)
    mov ecx, 0x55       ; arg in ecx
    int 0x80            ; software interrupt → IDT[0x80] handler (CD 80)
    hlt                 ; resumes here after IRET, then stops
