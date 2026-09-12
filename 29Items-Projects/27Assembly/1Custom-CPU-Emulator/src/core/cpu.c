/*
 * cpu.c — Machine orchestration: the fetch-decode-execute-retire loop.
 *
 * Execution model: RIP is advanced to the *next* instruction before the body
 * runs (matching x86 semantics), so control-flow ops adjust RIP relative to the
 * already-advanced value and RIP-relative addressing resolves correctly.
 */
#include "core/cpu.h"
#include "core/alu.h"
#include "common/log.h"

#include <stdlib.h>
#include <string.h>

/* -------------------------------------------------------------------------- */
cpu_t *cpu_create(size_t mem_size, cpu_model_t model) {
    cpu_t *cpu = (cpu_t *)calloc(1, sizeof *cpu);
    if (cpu == NULL) {
        return NULL;
    }
    if (mem_init(&cpu->mem, mem_size) != EMU_OK) {
        free(cpu);
        return NULL;
    }
    cpu->model = (model < CPU_MODEL_COUNT) ? model : CPU_MODEL_SKYLAKE;
    cpu_reset(cpu);
    return cpu;
}

void cpu_destroy(cpu_t *cpu) {
    if (cpu == NULL) {
        return;
    }
    mem_free(&cpu->mem);
    free(cpu);
}

void cpu_reset(cpu_t *cpu) {
    if (cpu == NULL) {
        return;
    }
    regfile_reset(&cpu->regs);
    idt_init(&cpu->idt);
    timing_init(&cpu->timing, cpu->model);
    /* Stack grows down from the top of guest RAM. */
    cpu->regs.gpr[REG_RSP] = cpu->mem.size;
    cpu->halted = false;
    cpu->instret = 0;
    cpu->last_status = EMU_OK;
    memset(&cpu->last_insn, 0, sizeof cpu->last_insn);
}

emu_status_t cpu_load_program(cpu_t *cpu, const uint8_t *code, size_t len,
                              uint64_t load_addr) {
    if (cpu == NULL || code == NULL) {
        return EMU_ERR_NULL;
    }
    emu_status_t st = mem_write(&cpu->mem, load_addr, code, len);
    if (st != EMU_OK) {
        return st;
    }
    cpu->regs.rip = load_addr;
    log_info("loaded %zu bytes at 0x%llx (entry=0x%llx)", len,
             (unsigned long long)load_addr, (unsigned long long)load_addr);
    return EMU_OK;
}

/* ----- stack -------------------------------------------------------------- */
emu_status_t cpu_push(cpu_t *cpu, uint64_t value, uint8_t width) {
    uint64_t sp = cpu->regs.gpr[REG_RSP] - width;
    emu_status_t st = mem_write_width(&cpu->mem, sp, width, value);
    if (st != EMU_OK) {
        return st;
    }
    cpu->regs.gpr[REG_RSP] = sp;
    return EMU_OK;
}

emu_status_t cpu_pop(cpu_t *cpu, uint64_t *out, uint8_t width) {
    uint64_t sp = cpu->regs.gpr[REG_RSP];
    emu_status_t st = mem_read_width(&cpu->mem, sp, width, out);
    if (st != EMU_OK) {
        return st;
    }
    cpu->regs.gpr[REG_RSP] = sp + width;
    return EMU_OK;
}

/* ----- operand access ----------------------------------------------------- */
static void effective_address(cpu_t *cpu, const operand_t *o, uint64_t *out) {
    uint64_t a = (uint64_t)o->mem_disp;
    if (o->rip_relative) {
        a += cpu->regs.rip; /* RIP already points at the next instruction */
    } else {
        if (o->mem_base != REG_NONE) {
            a += reg_read(&cpu->regs, o->mem_base, WIDTH_QWORD);
        }
        if (o->mem_index != REG_NONE) {
            a += reg_read(&cpu->regs, o->mem_index, WIDTH_QWORD) * o->mem_scale;
        }
    }
    *out = a;
}

static emu_status_t read_operand(cpu_t *cpu, const operand_t *o, uint64_t *val) {
    switch (o->kind) {
        case OPERAND_REG: *val = reg_read(&cpu->regs, o->reg, o->width); return EMU_OK;
        case OPERAND_IMM: *val = o->imm; return EMU_OK;
        case OPERAND_MEM: {
            uint64_t ea; effective_address(cpu, o, &ea);
            return mem_read_width(&cpu->mem, ea, o->width, val);
        }
        default: return EMU_ERR_DECODE;
    }
}

static emu_status_t write_operand(cpu_t *cpu, const operand_t *o, uint64_t val) {
    switch (o->kind) {
        case OPERAND_REG: reg_write(&cpu->regs, o->reg, o->width, val); return EMU_OK;
        case OPERAND_MEM: {
            uint64_t ea; effective_address(cpu, o, &ea);
            return mem_write_width(&cpu->mem, ea, o->width, val);
        }
        default: return EMU_ERR_DECODE; /* can't write to an immediate */
    }
}

/* ----- branch condition evaluation --------------------------------------- */
static bool branch_taken(const cpu_t *cpu, opcode_t op) {
    bool zf = flag_get(&cpu->regs, FLAG_ZF);
    bool sf = flag_get(&cpu->regs, FLAG_SF);
    bool of = flag_get(&cpu->regs, FLAG_OF);
    switch (op) {
        case OP_JMP: return true;
        case OP_JE:  return zf;
        case OP_JNE: return !zf;
        case OP_JL:  return sf != of;
        case OP_JGE: return sf == of;
        case OP_JLE: return zf || (sf != of);
        case OP_JG:  return !zf && (sf == of);
        default:     return false;
    }
}

/* Is this opcode handled by the two-operand ALU path? */
static bool is_binary_alu(opcode_t op) {
    switch (op) {
        case OP_ADD: case OP_SUB: case OP_ADC: case OP_SBB:
        case OP_AND: case OP_OR:  case OP_XOR:
        case OP_CMP: case OP_TEST:
        case OP_SHL: case OP_SHR: case OP_SAR:
            return true;
        default: return false;
    }
}
static bool is_unary_alu(opcode_t op) {
    return op == OP_INC || op == OP_DEC || op == OP_NEG || op == OP_NOT;
}

/* ----- the core step ------------------------------------------------------ */
emu_status_t cpu_step(cpu_t *cpu) {
    if (cpu == NULL) {
        return EMU_ERR_NULL;
    }
    if (cpu->halted) {
        return EMU_ERR_HALT;
    }

    instruction_t insn;
    uint64_t pc = cpu->regs.rip;
    emu_status_t st = decode_instruction(&cpu->mem, pc, &insn);
    if (st == EMU_ERR_INVALID_OPCODE) {
        cpu->last_status = st;
        return cpu_raise_interrupt(cpu, VEC_INVALID_OPCODE);
    }
    if (st != EMU_OK) {
        cpu->halted = true;
        cpu->last_status = st;
        return st;
    }
    cpu->last_insn = insn;

    /* Advance RIP to the next instruction up front. */
    cpu->regs.rip = pc + insn.length;

    st = EMU_OK;
    uint64_t a = 0, b = 0;

    if (is_binary_alu(insn.op)) {
        if ((st = read_operand(cpu, &insn.dst, &a)) != EMU_OK) goto fault;
        if ((st = read_operand(cpu, &insn.src, &b)) != EMU_OK) goto fault;
        alu_result_t r = alu_execute(insn.op, a, b, insn.dst.width, cpu->regs.rflags);
        cpu->regs.rflags = r.rflags;
        if (insn.op != OP_CMP && insn.op != OP_TEST) {
            if ((st = write_operand(cpu, &insn.dst, r.result)) != EMU_OK) goto fault;
        }
    } else if (is_unary_alu(insn.op)) {
        if ((st = read_operand(cpu, &insn.dst, &a)) != EMU_OK) goto fault;
        alu_result_t r = alu_execute(insn.op, a, 0, insn.dst.width, cpu->regs.rflags);
        cpu->regs.rflags = r.rflags;
        if ((st = write_operand(cpu, &insn.dst, r.result)) != EMU_OK) goto fault;
    } else {
        switch (insn.op) {
        case OP_NOP: break;

        case OP_MOV:
            if ((st = read_operand(cpu, &insn.src, &b)) != EMU_OK) goto fault;
            if ((st = write_operand(cpu, &insn.dst, b)) != EMU_OK) goto fault;
            break;

        case OP_LEA: { /* dst = effective address of src (no memory access) */
            uint64_t ea;
            effective_address(cpu, &insn.src, &ea);
            if ((st = write_operand(cpu, &insn.dst, ea)) != EMU_OK) goto fault;
            break;
        }

        case OP_PUSH:
            if ((st = read_operand(cpu, &insn.dst, &a)) != EMU_OK) goto fault;
            if ((st = cpu_push(cpu, a, WIDTH_QWORD)) != EMU_OK) goto fault;
            break;
        case OP_POP:
            if ((st = cpu_pop(cpu, &a, WIDTH_QWORD)) != EMU_OK) goto fault;
            if ((st = write_operand(cpu, &insn.dst, a)) != EMU_OK) goto fault;
            break;

        case OP_JMP: case OP_JE: case OP_JNE:
        case OP_JL:  case OP_JLE: case OP_JG: case OP_JGE:
            if (branch_taken(cpu, insn.op)) {
                cpu->regs.rip += (int64_t)insn.dst.imm;
            }
            break;

        case OP_CALL:
            if ((st = cpu_push(cpu, cpu->regs.rip, WIDTH_QWORD)) != EMU_OK) goto fault;
            cpu->regs.rip += (int64_t)insn.dst.imm;
            break;
        case OP_RET:
            if ((st = cpu_pop(cpu, &a, WIDTH_QWORD)) != EMU_OK) goto fault;
            cpu->regs.rip = a;
            break;

        case OP_LOOP: { /* dec RCX, jump if RCX != 0 */
            uint64_t cx = reg_read(&cpu->regs, REG_RCX, WIDTH_QWORD) - 1;
            reg_write(&cpu->regs, REG_RCX, WIDTH_QWORD, cx);
            if (cx != 0) {
                cpu->regs.rip += (int64_t)insn.dst.imm;
            }
            break;
        }

        case OP_MUL: case OP_IMUL: {
            if ((st = read_operand(cpu, &insn.dst, &b)) != EMU_OK) goto fault;
            uint64_t rax = reg_read(&cpu->regs, REG_RAX, insn.dst.width);
            alu_result_t r = alu_execute(insn.op, rax, b, insn.dst.width, cpu->regs.rflags);
            cpu->regs.rflags = r.rflags;
            reg_write(&cpu->regs, REG_RAX, insn.dst.width, r.result);
            /* TODO: write the high half of the product into RDX. */
            break;
        }
        case OP_DIV: case OP_IDIV: {
            if ((st = read_operand(cpu, &insn.dst, &b)) != EMU_OK) goto fault;
            if (b == 0) { st = EMU_ERR_DIV_ZERO; goto fault; }
            uint64_t rax = reg_read(&cpu->regs, REG_RAX, insn.dst.width);
            reg_write(&cpu->regs, REG_RAX, insn.dst.width, rax / b);
            reg_write(&cpu->regs, REG_RDX, insn.dst.width, rax % b);
            break;
        }

        case OP_INT:
            /* RIP already points to the return address. */
            st = cpu_raise_interrupt(cpu, (uint8_t)insn.dst.imm);
            if (st != EMU_OK) goto fault;
            break;
        case OP_IRET:
            if ((st = cpu_pop(cpu, &a, WIDTH_QWORD)) != EMU_OK) goto fault; /* rip   */
            cpu->regs.rip = a;
            if ((st = cpu_pop(cpu, &b, WIDTH_QWORD)) != EMU_OK) goto fault; /* rflags*/
            cpu->regs.rflags = b;
            break;

        case OP_HLT:
            cpu->halted = true;
            timing_account(&cpu->timing, &insn);
            cpu->instret++;
            cpu->last_status = EMU_ERR_HALT;
            return EMU_ERR_HALT;

        default:
            cpu->last_status = EMU_ERR_INVALID_OPCODE;
            return cpu_raise_interrupt(cpu, VEC_INVALID_OPCODE);
        }
    }

    timing_account(&cpu->timing, &insn);
    cpu->instret++;
    cpu->last_status = EMU_OK;
    return EMU_OK;

fault:
    cpu->last_status = st;
    if (st == EMU_ERR_DIV_ZERO) {
        return cpu_raise_interrupt(cpu, VEC_DIVIDE_ERROR);
    }
    if (st == EMU_ERR_MEM_BOUNDS) {
        return cpu_raise_interrupt(cpu, VEC_GENERAL_PROTECT);
    }
    cpu->halted = true;
    return st;
}

emu_status_t cpu_run(cpu_t *cpu, uint64_t max_steps) {
    if (cpu == NULL) {
        return EMU_ERR_NULL;
    }
    uint64_t steps = 0;
    for (;;) {
        if (cpu->halted) {
            return EMU_ERR_HALT;
        }
        if (max_steps != 0 && steps >= max_steps) {
            log_info("step cap %llu reached", (unsigned long long)max_steps);
            return EMU_OK;
        }
        emu_status_t st = cpu_step(cpu);
        steps++;
        if (st == EMU_ERR_HALT) {
            return EMU_ERR_HALT;
        }
        if (st != EMU_OK) {
            /* A handled fault (jumped into a handler) returns EMU_OK from step;
             * reaching here means an unhandled/fatal condition. */
            return st;
        }
    }
}
