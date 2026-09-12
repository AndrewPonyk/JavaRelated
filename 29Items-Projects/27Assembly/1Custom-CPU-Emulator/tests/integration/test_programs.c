/*
 * test_programs.c — End-to-end: load machine code, run to HLT, assert state.
 * Also validates the per-model timing estimates and snapshot round-tripping.
 */
#include "test_framework.h"
#include "core/cpu.h"
#include "core/registers.h"
#include "loader/loader.h"

#include <stdio.h>

#define HAS(f, m) (((f) & (uint64_t)(m)) != 0)
#define LOAD_ADDR 0x1000ull

int main(void) {
    TEST_SUITE("programs");

    /* Program 1:  mov eax,5 ; mov ecx,7 ; add eax,ecx ; hlt   → eax = 12 */
    static const uint8_t prog_add[] = {
        0xB8, 0x05, 0x00, 0x00, 0x00,
        0xB9, 0x07, 0x00, 0x00, 0x00,
        0x01, 0xC8,
        0xF4
    };

    cpu_t *cpu = cpu_create(64 * 1024, CPU_MODEL_SKYLAKE);
    CHECK_TRUE(cpu != NULL);
    CHECK_TRUE(cpu_load_program(cpu, prog_add, sizeof prog_add, LOAD_ADDR) == EMU_OK);
    CHECK_TRUE(cpu_run(cpu, 1000) == EMU_ERR_HALT);
    CHECK_TRUE(cpu->halted);
    CHECK_EQ_U64(reg_read(&cpu->regs, REG_RAX, WIDTH_DWORD), 12);
    CHECK_EQ_U64(cpu->instret, 4);
    /* Skylake: mov(1)+mov(1)+add(1)+hlt(8) = 11 cycles. */
    CHECK_EQ_U64(cpu->timing.total_cycles, 11);

    /* Same program on an 8086 must cost (a lot) more cycles. */
    cpu_t *old = cpu_create(64 * 1024, CPU_MODEL_8086);
    CHECK_TRUE(cpu_load_program(old, prog_add, sizeof prog_add, LOAD_ADDR) == EMU_OK);
    cpu_run(old, 1000);
    CHECK_TRUE(old->timing.total_cycles > cpu->timing.total_cycles);

    /* Program 2:  mov eax,10 ; sub eax,16 ; hlt   → eax = -6, CF & SF set */
    static const uint8_t prog_sub[] = {
        0xB8, 0x0A, 0x00, 0x00, 0x00,  /* mov eax, 10        */
        0x83, 0xE8, 0x10,              /* sub eax, 0x10      */
        0xF4                           /* hlt                */
    };
    cpu_t *c2 = cpu_create(64 * 1024, CPU_MODEL_SKYLAKE);
    cpu_load_program(c2, prog_sub, sizeof prog_sub, LOAD_ADDR);
    CHECK_TRUE(cpu_run(c2, 1000) == EMU_ERR_HALT);
    CHECK_EQ_U64(reg_read(&c2->regs, REG_RAX, WIDTH_DWORD), 0xFFFFFFFAu);
    CHECK_TRUE(HAS(c2->regs.rflags, FLAG_CF));
    CHECK_TRUE(HAS(c2->regs.rflags, FLAG_SF));

    /* Program 3: a counted loop using a backward JNE branch → eax = 15. */
    static const uint8_t prog_loop[] = {
        0xB8, 0x00, 0x00, 0x00, 0x00,  /* mov eax, 0           */
        0xB9, 0x05, 0x00, 0x00, 0x00,  /* mov ecx, 5           */
        0x01, 0xC8,                    /* .loop: add eax, ecx  */
        0x83, 0xE9, 0x01,              /* sub ecx, 1           */
        0x83, 0xF9, 0x00,              /* cmp ecx, 0           */
        0x75, 0xF6,                    /* jne .loop (rel8 -10) */
        0xF4                           /* hlt                  */
    };
    cpu_t *c3 = cpu_create(64 * 1024, CPU_MODEL_SKYLAKE);
    cpu_load_program(c3, prog_loop, sizeof prog_loop, LOAD_ADDR);
    CHECK_TRUE(cpu_run(c3, 1000) == EMU_ERR_HALT);
    CHECK_EQ_U64(reg_read(&c3->regs, REG_RAX, WIDTH_DWORD), 15);
    CHECK_EQ_U64(reg_read(&c3->regs, REG_RCX, WIDTH_DWORD), 0);

    /* Edge case: a program too large for RAM must fail gracefully, not crash. */
    cpu_t *tiny = cpu_create(8, CPU_MODEL_SKYLAKE);
    uint8_t big[64] = {0};
    CHECK_TRUE(cpu_load_program(tiny, big, sizeof big, 0) == EMU_ERR_MEM_BOUNDS);
    cpu_destroy(tiny);

    /* Program 4: CALL/RET round-trip — func sets eax=0x99; RSP must balance. */
    static const uint8_t prog_call[] = {
        0xE8, 0x01, 0x00, 0x00, 0x00,  /* call func (rel32 +1)  @1000 */
        0xF4,                          /* hlt                   @1005 */
        0xB8, 0x99, 0x00, 0x00, 0x00,  /* func: mov eax, 0x99   @1006 */
        0xC3                           /* ret                   @100B */
    };
    cpu_t *c4 = cpu_create(64 * 1024, CPU_MODEL_SKYLAKE);
    cpu_load_program(c4, prog_call, sizeof prog_call, LOAD_ADDR);
    CHECK_TRUE(cpu_run(c4, 1000) == EMU_ERR_HALT);
    CHECK_EQ_U64(reg_read(&c4->regs, REG_RAX, WIDTH_DWORD), 0x99);
    CHECK_EQ_U64(reg_read(&c4->regs, REG_RSP, WIDTH_QWORD), 64 * 1024); /* balanced */
    cpu_destroy(c4);

    /* Program 5: PUSH/POP move a value rax → rbx through the stack. */
    static const uint8_t prog_stack[] = {
        0xB8, 0x34, 0x12, 0x00, 0x00,  /* mov eax, 0x1234 */
        0x50,                          /* push rax        */
        0xB8, 0x00, 0x00, 0x00, 0x00,  /* mov eax, 0      */
        0x5B,                          /* pop rbx         */
        0xF4                           /* hlt             */
    };
    cpu_t *c5 = cpu_create(64 * 1024, CPU_MODEL_SKYLAKE);
    cpu_load_program(c5, prog_stack, sizeof prog_stack, LOAD_ADDR);
    CHECK_TRUE(cpu_run(c5, 1000) == EMU_ERR_HALT);
    CHECK_EQ_U64(reg_read(&c5->regs, REG_RBX, WIDTH_QWORD), 0x1234);
    CHECK_EQ_U64(reg_read(&c5->regs, REG_RAX, WIDTH_DWORD), 0);
    CHECK_EQ_U64(reg_read(&c5->regs, REG_RSP, WIDTH_QWORD), 64 * 1024); /* balanced */
    cpu_destroy(c5);

    /* Program 6: memory operands — store eax to [rbx], load it back into ecx.
     * Exercises ModR/M memory decode, effective-address calc, and mem r/w. */
    static const uint8_t prog_mem[] = {
        0xBB, 0x00, 0x01, 0x00, 0x00,  /* mov ebx, 0x100  (data address) */
        0xB8, 0x34, 0x12, 0x00, 0x00,  /* mov eax, 0x1234                */
        0x89, 0x03,                    /* mov [rbx], eax  (store)        */
        0x8B, 0x0B,                    /* mov ecx, [rbx]  (load)         */
        0xF4                           /* hlt                            */
    };
    cpu_t *c6 = cpu_create(64 * 1024, CPU_MODEL_SKYLAKE);
    cpu_load_program(c6, prog_mem, sizeof prog_mem, LOAD_ADDR);
    CHECK_TRUE(cpu_run(c6, 1000) == EMU_ERR_HALT);
    CHECK_EQ_U64(reg_read(&c6->regs, REG_RCX, WIDTH_DWORD), 0x1234); /* round-tripped via RAM */
    cpu_destroy(c6);

    /* Snapshot round-trip: save program-1's final state, restore, compare. */
    const char *path = "test_snapshot.ces";
    CHECK_TRUE(loader_save_state(cpu, path) == EMU_OK);
    cpu_t *restored = cpu_create(64 * 1024, CPU_MODEL_SKYLAKE);
    CHECK_TRUE(loader_load_state(restored, path) == EMU_OK);
    CHECK_EQ_U64(reg_read(&restored->regs, REG_RAX, WIDTH_DWORD), 12);
    CHECK_EQ_U64(restored->instret, cpu->instret);
    CHECK_EQ_U64(restored->timing.total_cycles, cpu->timing.total_cycles);
    remove(path);

    /* File-loader round-trip: write a flat binary to disk, load it, run it. */
    {
        const char *binpath = "test_prog.bin";
        FILE *bf = fopen(binpath, "wb");
        CHECK_TRUE(bf != NULL);
        if (bf != NULL) {
            fwrite(prog_add, 1, sizeof prog_add, bf);
            fclose(bf);
            cpu_t *cf = cpu_create(64 * 1024, CPU_MODEL_SKYLAKE);
            CHECK_TRUE(loader_load_flat_binary(cf, binpath, LOAD_ADDR) == EMU_OK);
            CHECK_TRUE(cpu_run(cf, 1000) == EMU_ERR_HALT);
            CHECK_EQ_U64(reg_read(&cf->regs, REG_RAX, WIDTH_DWORD), 12);
            cpu_destroy(cf);
            remove(binpath);
        }
    }

    /* Truncated snapshot must fail AND leave the CPU unchanged (atomic restore). */
    {
        const char *full = "test_full.ces";
        const char *trunc = "test_trunc.ces";
        loader_save_state(cpu, full);                 /* valid snapshot (eax=12) */
        FILE *src = fopen(full, "rb");
        uint8_t hd[40];                               /* header (36B) + a partial reg */
        size_t got = (src != NULL) ? fread(hd, 1, sizeof hd, src) : 0;
        if (src != NULL) fclose(src);
        FILE *dst = fopen(trunc, "wb");
        if (dst != NULL) { fwrite(hd, 1, got, dst); fclose(dst); }

        cpu_t *victim = cpu_create(64 * 1024, CPU_MODEL_SKYLAKE);
        reg_write(&victim->regs, REG_RAX, WIDTH_QWORD, 0xCAFEULL);
        CHECK_TRUE(loader_load_state(victim, trunc) == EMU_ERR_IO);
        CHECK_EQ_U64(reg_read(&victim->regs, REG_RAX, WIDTH_QWORD), 0xCAFE); /* untouched */
        cpu_destroy(victim);
        remove(full);
        remove(trunc);
    }

    cpu_destroy(cpu);
    cpu_destroy(old);
    cpu_destroy(c2);
    cpu_destroy(c3);
    cpu_destroy(restored);
    return test_report();
}
