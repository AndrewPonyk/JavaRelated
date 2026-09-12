; boot.asm — Multiboot2 header + 32-bit→64-bit long-mode bring-up (NASM).
;
; GRUB (via grub-mkrescue ISO) parses the Multiboot2 header, loads our ELF by
; its program headers, and jumps to `_start` in 32-bit protected mode. We:
;   1. set up 4-level paging: identity-map the low 1 GiB AND map the kernel's
;      higher-half window (0xFFFFFFFF80000000+) to the same physical memory,
;   2. enable PAE + long mode + paging,
;   3. load a 64-bit GDT and far-jump into 64-bit code,
;   4. relocate RIP into the higher half and call C `kernel_main`.
;
; Early boot code, page tables and the boot stack live in section .boot, which
; the linker places at LOW physical addresses so they are directly addressable
; in 32-bit mode (no vaddr arithmetic needed).

KERNEL_VMA       equ 0xFFFFFFFF80000000
PRESENT_WRITABLE equ 0b11
HUGE_PRESENT_RW  equ 0b10000011        ; present | writable | huge (2 MiB)

; ---------------------------------------------------------------------------
section .multiboot_header
align 8
mb_header_start:
    dd 0xE85250D6                                   ; Multiboot2 magic
    dd 0                                            ; architecture: i386
    dd mb_header_end - mb_header_start              ; header length
    dd 0x100000000 - (0xE85250D6 + 0 + (mb_header_end - mb_header_start))
    ; end tag
    dw 0
    dw 0
    dd 8
mb_header_end:

; ---------------------------------------------------------------------------
section .boot
bits 32
global _start

_start:
    mov esp, boot_stack_top

    ; Stash the Multiboot2 magic (eax) and info pointer (ebx) for the C side.
    mov [mb_magic], eax
    mov [mb_info],  ebx

    ; --- Build page tables -------------------------------------------------
    ; PML4[0]   -> low  PDPT   (identity map of 0..1 GiB)
    ; PML4[511] -> high PDPT   (higher-half kernel window)
    mov eax, boot_p3_low
    or  eax, PRESENT_WRITABLE
    mov [boot_p4 + 0 * 8], eax

    mov eax, boot_p3_high
    or  eax, PRESENT_WRITABLE
    mov [boot_p4 + 511 * 8], eax

    ; low  PDPT[0]   -> PD   (identity)
    mov eax, boot_p2
    or  eax, PRESENT_WRITABLE
    mov [boot_p3_low + 0 * 8], eax

    ; high PDPT[510] -> PD   (0xFFFFFFFF80000000 >> 30 & 0x1FF == 510)
    mov eax, boot_p2
    or  eax, PRESENT_WRITABLE
    mov [boot_p3_high + 510 * 8], eax

    ; Fill the PD with 512 × 2 MiB identity pages -> maps the first 1 GiB.
    mov ecx, 0
.map_pd:
    mov eax, 0x200000          ; 2 MiB
    mul ecx                    ; eax = 2 MiB * ecx  (edx cleared; < 1 GiB fits 32-bit)
    or  eax, HUGE_PRESENT_RW
    mov [boot_p2 + ecx * 8], eax
    inc ecx
    cmp ecx, 512
    jne .map_pd

    ; --- Enable long mode --------------------------------------------------
    mov eax, boot_p4
    mov cr3, eax               ; load page tables

    mov eax, cr4
    or  eax, 1 << 5            ; CR4.PAE
    mov cr4, eax

    mov ecx, 0xC0000080        ; EFER MSR
    rdmsr
    or  eax, 1 << 8            ; EFER.LME (long mode enable)
    wrmsr

    mov eax, cr0
    or  eax, 1 << 31           ; CR0.PG (paging) — PE already set by GRUB
    mov cr0, eax

    ; --- Enter 64-bit mode -------------------------------------------------
    lgdt [gdt64.pointer]
    jmp  gdt64.code:long_mode_start

bits 64
long_mode_start:
    ; Reload data segment registers with the 64-bit data selector.
    mov ax, gdt64.data
    mov ds, ax
    mov es, ax
    mov ss, ax
    mov fs, ax
    mov gs, ax

    ; Relocate RIP into the higher half and continue there.
    mov rax, higher_half_start
    jmp rax

; ---------------------------------------------------------------------------
; 64-bit higher-half entry. Lives in .text (high virtual addresses).
section .text
bits 64
global higher_half_start
extern kernel_main

higher_half_start:
    mov rsp, kernel_stack_top      ; switch to the high-mapped kernel stack
    xor rbp, rbp

    ; Pass the Multiboot2 info pointer (physical, identity-mapped) to C.
    mov rax, mb_info
    mov edi, [rax]                 ; arg0 = mb_info (zero-extended into rdi)
    call kernel_main

.hang:
    cli
    hlt
    jmp .hang

; ---------------------------------------------------------------------------
; Low boot data: GDT, page tables, boot stack, saved multiboot registers.
section .boot
align 8
gdt64:
    dq 0                                            ; null descriptor
.code: equ $ - gdt64
    dq (1 << 43) | (1 << 44) | (1 << 47) | (1 << 53) ; 64-bit code segment
.data: equ $ - gdt64
    dq (1 << 41) | (1 << 44) | (1 << 47)             ; data segment
.pointer:
    dw $ - gdt64 - 1
    dd gdt64                                         ; 32-bit base (low address)

global mb_magic
global mb_info
mb_magic: dd 0
mb_info:  dd 0

align 4096
boot_p4:      times 512 dq 0
boot_p3_low:  times 512 dq 0
boot_p3_high: times 512 dq 0
boot_p2:      times 512 dq 0

align 16
boot_stack_bottom:
    times 4096 db 0
boot_stack_top:

; ---------------------------------------------------------------------------
; The real kernel stack, in high-mapped .bss.
section .bss
align 16
global kernel_stack_bottom
kernel_stack_bottom:
    resb 65536                                      ; 64 KiB
global kernel_stack_top
kernel_stack_top:

; Mark the stack as non-executable (silences the linker's GNU-stack warning).
section .note.GNU-stack noalloc noexec nowrite progbits
