; ============================================================================
;  string_ops_avx2.asm — AVX2 string / memory scan kernels (NASM, Intel syntax)
;
;  ABI: System V AMD64. Requires AVX2 + BMI1 (tzcnt). Validated against the C
;  reference (and libc) by tests/test_string.c, including the page-boundary case.
;
;  Page-safety: scans align the pointer DOWN to a 32-byte boundary and mask out
;  bytes before the true start. A 32-byte-aligned load never crosses a 4 KiB page
;  (4096 % 32 == 0), so the scan can never fault by reading past the terminator
;  into an unmapped page.
; ============================================================================

default rel
section .text

global perflib_strlen_avx2
global perflib_memchr_avx2

; ----------------------------------------------------------------------------
; size_t perflib_strlen_avx2(const char *s)      rdi = s ; returns length in rax
; ----------------------------------------------------------------------------
perflib_strlen_avx2:
        vpxor       ymm0, ymm0, ymm0      ; ymm0 = 0 (compare target)
        mov         r8, rdi               ; r8 = original start (for length calc)
        mov         ecx, edi
        and         ecx, 31               ; misalignment 0..31 (low bits of ptr)
        and         rdi, -32              ; align pointer down to 32B

        vmovdqa     ymm1, [rdi]           ; aligned 32B load (page-safe)
        vpcmpeqb    ymm1, ymm1, ymm0      ; FF in lanes where byte == 0
        vpmovmskb   eax, ymm1             ; bit i set => byte i is NUL
        shr         eax, cl               ; discard bytes before the real start
        test        eax, eax              ; NOTE: shr by 0 leaves flags untouched,
        jnz         .found_first          ; so set them explicitly before branching

        ; Re-align scan cursor to the aligned block start, then stride by 32.
        ; (We shifted out 'cl' low bits; the next aligned block is rdi+32.)
.loop:
        add         rdi, 32
        vmovdqa     ymm1, [rdi]
        vpcmpeqb    ymm1, ymm1, ymm0
        vpmovmskb   eax, ymm1
        test        eax, eax
        jz          .loop

        ; NUL found in block at rdi; index within block = tzcnt(eax).
        tzcnt       eax, eax              ; BMI1
        sub         rdi, r8               ; (aligned_block - start)
        add         rax, rdi              ; length = (block-start) + index
        vzeroupper
        ret

.found_first:
        ; eax was right-shifted by cl, so bit 0 == original start byte;
        ; tzcnt gives the distance from start to the NUL == the length.
        tzcnt       eax, eax
        vzeroupper
        ret

; ----------------------------------------------------------------------------
; void *perflib_memchr_avx2(const void *s, int c, size_t n)
;   rdi = s, esi = c, rdx = n ; returns first match or NULL in rax.
;   Scans full 32-byte chunks with vpcmpeqb; the < 32-byte remainder is handled
;   one byte at a time, so the scan never reads past s+n (no over-read into an
;   adjacent page). Requires AVX2 + BMI1 (tzcnt).
; ----------------------------------------------------------------------------
perflib_memchr_avx2:
        movzx        eax, sil             ; (unsigned char)c
        vmovd        xmm1, eax
        vpbroadcastb ymm1, xmm1           ; ymm1 = c repeated x32 (AVX2)
        xor          rax, rax             ; i = 0
        mov          rcx, rdx
        and          rcx, -32             ; full-chunk limit = n & ~31
        cmp          rax, rcx
        jae          .tail
.loop:
        vmovdqu      ymm2, [rdi + rax]
        vpcmpeqb     ymm2, ymm2, ymm1
        vpmovmskb    r8d, ymm2
        test         r8d, r8d
        jnz          .hit_vec
        add          rax, 32
        cmp          rax, rcx
        jb           .loop
.tail:
        cmp          rax, rdx             ; scalar remainder [i, n)
        jae          .notfound
        movzx        r8d, byte [rdi + rax]
        cmp          r8b, sil
        je           .hit_scalar
        inc          rax
        jmp          .tail
.hit_vec:
        tzcnt        r8d, r8d             ; first matching lane within the chunk
        add          rax, r8              ; absolute index
.hit_scalar:
        lea          rax, [rdi + rax]     ; return s + index
        vzeroupper
        ret
.notfound:
        xor          eax, eax             ; NULL
        vzeroupper
        ret

; Non-executable stack marker (ELF).
section .note.GNU-stack noalloc noexec nowrite progbits
