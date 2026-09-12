/*
 * test_interrupts.c — end-to-end interrupt/exception delivery.
 * Closes the "lightly tested" gap: full INT → handler → IRET round-trip, the
 * unhandled-vector fault path, and invalid-opcode (#UD) delivery to a handler.
 */
#include "test_framework.h"
#include "core/cpu.h"
#include "core/registers.h"
#include "core/interrupts.h"

#define ENTRY   0x1000ull
#define HANDLER 0x2000ull

int main(void) {
    TEST_SUITE("interrupts");

    /* (1) INT 0x80 enters a handler that sets eax, then IRET resumes to HLT. */
    {
        cpu_t *cpu = cpu_create(64 * 1024, CPU_MODEL_SKYLAKE);
        idt_set_handler(&cpu->idt, 0x80, HANDLER);

        static const uint8_t prog[]    = { 0xCD, 0x80, 0xF4 };           /* int 0x80; hlt */
        static const uint8_t handler[] = { 0xB8, 0xAB, 0x00, 0x00, 0x00, /* mov eax,0xAB  */
                                           0xCF };                        /* iret          */
        cpu_load_program(cpu, prog, sizeof prog, ENTRY);
        mem_write(&cpu->mem, HANDLER, handler, sizeof handler);

        CHECK_TRUE(cpu_run(cpu, 100) == EMU_ERR_HALT);
        CHECK_TRUE(cpu->halted);
        CHECK_EQ_U64(reg_read(&cpu->regs, REG_RAX, WIDTH_DWORD), 0xAB); /* handler ran   */
        CHECK_EQ_U64(cpu->regs.rip, ENTRY + 3);                        /* resumed to hlt */
        cpu_destroy(cpu);
    }

    /* (2) Unhandled software interrupt → fatal fault, halts, no host crash. */
    {
        cpu_t *cpu = cpu_create(64 * 1024, CPU_MODEL_SKYLAKE);
        static const uint8_t prog[] = { 0xCD, 0x40 }; /* int 0x40, no handler */
        cpu_load_program(cpu, prog, sizeof prog, ENTRY);

        emu_status_t st = cpu_step(cpu);
        CHECK_TRUE(st != EMU_OK);
        CHECK_TRUE(cpu->halted);
        cpu_destroy(cpu);
    }

    /* (3) Invalid opcode raises #UD (vector 6); a registered handler receives it. */
    {
        cpu_t *cpu = cpu_create(64 * 1024, CPU_MODEL_SKYLAKE);
        idt_set_handler(&cpu->idt, VEC_INVALID_OPCODE, HANDLER);
        static const uint8_t prog[]   = { 0x0F }; /* unsupported → #UD */
        static const uint8_t udh[]    = { 0xF4 }; /* handler: hlt      */
        cpu_load_program(cpu, prog, sizeof prog, ENTRY);
        mem_write(&cpu->mem, HANDLER, udh, sizeof udh);

        emu_status_t st = cpu_step(cpu); /* decode #UD → deliver to handler */
        CHECK_TRUE(st == EMU_OK);
        CHECK_EQ_U64(cpu->regs.rip, HANDLER);
        CHECK_FALSE(cpu->halted);
        cpu_destroy(cpu);
    }

    return test_report();
}
