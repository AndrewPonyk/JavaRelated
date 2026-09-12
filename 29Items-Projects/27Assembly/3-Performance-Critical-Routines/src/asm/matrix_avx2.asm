; ============================================================================
;  matrix_avx2.asm — AVX2 + FMA3 linear-algebra kernels (NASM, Intel syntax)
;
;  ABI: System V AMD64 (Linux/macOS, MinGW with sysv mapping).
;       int args:   rdi, rsi, rdx, rcx, r8, r9, then [rsp+8], [rsp+16], ...
;       float args: xmm0..xmm7   returns: rax (int) / xmm0 (float)
;       callee-saved: rbx, rbp, r12..r15
;       AVX hygiene: vzeroupper before returning to an SSE/scalar caller.
;
;  Requires AVX2 + FMA3 (Haswell+). The dispatcher only calls these after CPUID
;  confirms both. Every kernel is validated against the C reference, ULP-bounded,
;  by tests/test_matrix.c.
; ============================================================================

default rel
section .text

global perflib_saxpy_avx2
global perflib_sdot_avx2
global perflib_sgemm_avx2

; ----------------------------------------------------------------------------
; void perflib_saxpy_avx2(int n, float a, const float *x, float *y)
;   y[i] += a * x[i]      rdi=n  xmm0=a  rsi=x  rdx=y
;   8 floats/iter via FMA; scalar remainder for the tail.
; ----------------------------------------------------------------------------
perflib_saxpy_avx2:
        test    edi, edi
        jle     .done                    ; n <= 0
        vbroadcastss ymm0, xmm0           ; ymm0 = [a x8]; xmm0 low keeps a
        xor     rax, rax                  ; i = 0
        mov     ecx, edi
        and     ecx, -8                   ; main = n & ~7
        jz      .tail
.loop:
        vmovups ymm1, [rsi + rax*4]       ; x[i..i+7]
        vmovups ymm2, [rdx + rax*4]       ; y[i..i+7]
        vfmadd231ps ymm2, ymm1, ymm0      ; y = x*a + y
        vmovups [rdx + rax*4], ymm2
        add     rax, 8
        cmp     rax, rcx
        jl      .loop
.tail:
        mov     ecx, edi                  ; bound = n
.tail_loop:
        cmp     rax, rcx
        jge     .finish
        vmovss  xmm1, [rsi + rax*4]
        vmovss  xmm2, [rdx + rax*4]
        vfmadd231ss xmm2, xmm1, xmm0      ; a in xmm0 low lane
        vmovss  [rdx + rax*4], xmm2
        inc     rax
        jmp     .tail_loop
.finish:
        vzeroupper
.done:
        ret

; ----------------------------------------------------------------------------
; float perflib_sdot_avx2(int n, const float *x, const float *y)
;   returns sum_i x[i]*y[i] in xmm0      rdi=n  rsi=x  rdx=y
;   IMPORTANT: reduce the vector accumulator to a scalar BEFORE the scalar tail,
;   because scalar (VEX.128) ops zero the upper 128 bits of the ymm register.
; ----------------------------------------------------------------------------
perflib_sdot_avx2:
        vxorps  ymm0, ymm0, ymm0          ; acc = 0
        xor     rax, rax                  ; i = 0
        mov     r9d, edi                  ; tail bound = n
        test    edi, edi
        jle     .reduce
        mov     ecx, edi
        and     ecx, -8                   ; main = n & ~7
        jz      .reduce
.loop:
        vmovups ymm1, [rsi + rax*4]
        vfmadd231ps ymm0, ymm1, [rdx + rax*4]   ; acc += x*y
        add     rax, 8
        cmp     rax, rcx
        jl      .loop
.reduce:
        vextractf128 xmm1, ymm0, 1
        vaddps  xmm0, xmm0, xmm1          ; 4 partial sums
        vhaddps xmm0, xmm0, xmm0          ; 2
        vhaddps xmm0, xmm0, xmm0          ; 1 -> xmm0[31:0]
.tail:
        cmp     rax, r9
        jge     .finish
        vmovss  xmm1, [rsi + rax*4]
        vfmadd231ss xmm0, xmm1, [rdx + rax*4]
        inc     rax
        jmp     .tail
.finish:
        vzeroupper
        ret

; ----------------------------------------------------------------------------
; void perflib_sgemm_avx2(int M, int N, int K,
;                         const float *A, int lda,
;                         const float *B, int ldb,
;                         float *C, int ldc)
;   C := A*B + C, row-major.
;   regs : rdi=M rsi=N rdx=K rcx=A r8=lda r9=B
;   stack: [rsp+8]=ldb  [rsp+16]=C  [rsp+24]=ldc   (entry, before pushes)
;
;   ikj kernel: broadcast A[i,k], FMA an 8-wide column block of B into C.
;     for i: for k: a=A[i,k]; for j(step 8): C[i,j..]+=a*B[k,j..]
;
;   Correct for N a multiple of 8 (the vectorizable path). For N not a multiple
;   of 8 it computes only the first (N & ~7) columns, so dispatch.c routes such
;   N to the C reference until the masked epilogue lands.
;
;   TODO(perf): 6x16 register-blocked micro-kernel holding the C tile in YMM
;               across the K loop; L1/L2 cache blocking over M/N/K; SW prefetch.
;   TODO(correctness): vmaskmovps epilogue for the N % 8 columns.
; ----------------------------------------------------------------------------
perflib_sgemm_avx2:
        push    rbx
        push    rbp
        push    r12
        push    r13
        push    r14
        push    r15
        ; 6 pushes = 48 bytes; entry [rsp+8/16/24] are now [rsp+56/64/72].
        mov     r10, [rsp + 56]           ; ldb
        mov     r11, [rsp + 64]           ; C base
        mov     rbx, [rsp + 72]           ; ldc

        movsxd  rdi, edi                  ; M
        movsxd  rdx, edx                  ; K
        movsxd  r8,  r8d                  ; lda
        movsxd  r10, r10d                 ; ldb
        movsxd  rbx, ebx                  ; ldc

        mov     ebp, esi
        and     ebp, -8
        movsxd  rbp, ebp                  ; Nmain = N & ~7 (rsi/N then free as j)

        test    rdi, rdi
        jle     .gemm_done
        test    rbp, rbp
        jle     .gemm_done                ; N < 8 -> nothing here (tail = TODO)

        xor     r12, r12                  ; i = 0
.row_loop:
        mov     rax, r12
        imul    rax, rbx                  ; i*ldc
        lea     r14, [r11 + rax*4]        ; cRow = &C[i,0]
        mov     rax, r12
        imul    rax, r8                   ; i*lda
        lea     r15, [rcx + rax*4]        ; aRow = &A[i,0]

        xor     r13, r13                  ; k = 0
.k_loop:
        cmp     r13, rdx
        jge     .k_done
        vbroadcastss ymm0, [r15 + r13*4]  ; a = A[i,k]
        mov     rax, r13
        imul    rax, r10                  ; k*ldb
        lea     rax, [r9 + rax*4]         ; bRow = &B[k,0]

        xor     rsi, rsi                  ; j = 0
.col_loop:
        vmovups ymm1, [r14 + rsi*4]       ; C[i, j..j+7]
        vfmadd231ps ymm1, ymm0, [rax + rsi*4]  ; += a * B[k, j..j+7]
        vmovups [r14 + rsi*4], ymm1
        add     rsi, 8
        cmp     rsi, rbp
        jl      .col_loop

        inc     r13
        jmp     .k_loop
.k_done:
        inc     r12
        cmp     r12, rdi
        jl      .row_loop
.gemm_done:
        vzeroupper
        pop     r15
        pop     r14
        pop     r13
        pop     r12
        pop     rbp
        pop     rbx
        ret

; Non-executable stack marker (ELF). Ignored on non-ELF targets.
section .note.GNU-stack noalloc noexec nowrite progbits
