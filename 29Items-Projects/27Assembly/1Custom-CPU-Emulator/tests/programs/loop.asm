; loop.asm — a counted accumulation loop using only the supported subset.
; Assemble:  nasm -f bin loop.asm -o loop.bin
; Run:       cpuemu --program loop.bin --headless
; Computes   eax = 5 + 4 + 3 + 2 + 1 = 15 (0x0F), then halts.
;
; Demonstrates: immediate moves, register ADD, the 0x83 imm8 group (SUB/CMP),
; and a conditional branch (JNE rel8) driving the loop.

bits 64
org 0x1000

_start:
    mov eax, 0          ; accumulator
    mov ecx, 5          ; loop counter

.loop:
    add eax, ecx        ; eax += ecx          (01 /r)
    sub ecx, 1          ; ecx -= 1            (83 /5, imm8)
    cmp ecx, 0          ; sets ZF when done   (83 /7, imm8)
    jne .loop           ; repeat while ecx!=0 (75 rel8)

    hlt                 ; eax now holds 15
