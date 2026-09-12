; add.asm — basic arithmetic fixture for the emulator.
; Assemble to a flat binary:   nasm -f bin add.asm -o add.bin
; Run:                          cpuemu --program add.bin --model skylake --headless
; Expected final state:         eax = 0x0C (12), edx = 0x0F (15), halted.

bits 64
org 0x1000              ; must match the emulator's --load-addr

_start:
    mov eax, 5          ; eax = 5
    mov ecx, 7          ; ecx = 7
    add eax, ecx        ; eax = 12         (opcode 01 /r)
    mov edx, eax        ; edx = 12
    add edx, 3          ; edx = 15         (opcode 83 /0, imm8)
    hlt                 ; stop the machine
