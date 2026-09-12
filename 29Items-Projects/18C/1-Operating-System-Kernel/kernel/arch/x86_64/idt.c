/*
 * idt.c — Interrupt Descriptor Table + top-level interrupt dispatch.
 *
 * Installs 256 gate descriptors: 0-31 CPU exceptions, 32-47 the remapped PIC
 * IRQs (timer=32, keyboard=33), the rest a generic stub. The asm stubs in
 * isr.asm funnel into isr_handler() below with a saved register frame.
 */
#include "../../include/kernel.h"
#include "../../include/sched.h"
#include "io.h"

#define IDT_ENTRIES 256

struct idt_entry {
    uint16_t offset_low;
    uint16_t selector;
    uint8_t  ist;
    uint8_t  type_attr;
    uint16_t offset_mid;
    uint32_t offset_high;
    uint32_t zero;
} __attribute__((packed));

struct idt_ptr {
    uint16_t limit;
    uint64_t base;
} __attribute__((packed));

/* Register frame pushed by the asm stubs (low address = last pushed). */
typedef struct regs {
    uint64_t r15, r14, r13, r12, r11, r10, r9, r8;
    uint64_t rbp, rdi, rsi, rdx, rcx, rbx, rax;
    uint64_t int_no, err_code;
    uint64_t rip, cs, rflags, rsp, ss;   /* pushed by the CPU */
} regs_t;

static struct idt_entry idt[IDT_ENTRIES];
static struct idt_ptr   idtr;

/* From isr.asm */
extern void *isr_stub_table[];   /* vectors 0..47 */
extern void  isr_unhandled(void);

/* From pic.c / pit.c / keyboard.c */
void pic_send_eoi(uint8_t irq);
void timer_on_tick(void);
void keyboard_isr(void);

static const char *exception_name(uint64_t n)
{
    static const char *names[] = {
        "#DE divide error", "#DB debug", "NMI", "#BP breakpoint",
        "#OF overflow", "#BR bound range", "#UD invalid opcode",
        "#NM device not available", "#DF double fault", "coproc overrun",
        "#TS invalid TSS", "#NP segment not present", "#SS stack fault",
        "#GP general protection", "#PF page fault", "reserved",
        "#MF x87 fp", "#AC alignment", "#MC machine check", "#XM simd",
    };
    return n < (sizeof(names) / sizeof(names[0])) ? names[n] : "reserved";
}

static void set_gate(int vec, uint64_t handler, uint8_t type_attr)
{
    idt[vec].offset_low  = handler & 0xFFFF;
    idt[vec].selector    = 0x08;            /* 64-bit kernel code selector */
    idt[vec].ist         = 0;
    idt[vec].type_attr   = type_attr;       /* 0x8E = present, ring0, int gate */
    idt[vec].offset_mid  = (handler >> 16) & 0xFFFF;
    idt[vec].offset_high = (handler >> 32) & 0xFFFFFFFF;
    idt[vec].zero        = 0;
}

/* Called from isr_common with a pointer to the saved register frame. */
void isr_handler(regs_t *r)
{
    if (r->int_no < 32) {
        /* CPU exception — fatal in this educational kernel. */
        panic("CPU exception %u (%s) err=%x rip=%x",
              (unsigned)r->int_no, exception_name(r->int_no),
              (unsigned long)r->err_code, (unsigned long)r->rip);
    }

    uint8_t irq = (uint8_t)(r->int_no - 32);
    switch (irq) {
        case 0: timer_on_tick();   break;   /* PIT */
        case 1: keyboard_isr();    break;   /* PS/2 keyboard */
        default: break;                     /* ignore other IRQs */
    }
    pic_send_eoi(irq);
}

static inline void lidt(struct idt_ptr *p)
{
    __asm__ volatile("lidt %0" :: "m"(*p));
}

void idt_init(void)
{
    for (int i = 0; i < 48; i++)
        set_gate(i, (uint64_t)isr_stub_table[i], 0x8E);
    for (int i = 48; i < IDT_ENTRIES; i++)
        set_gate(i, (uint64_t)isr_unhandled, 0x8E);

    idtr.limit = sizeof(idt) - 1;
    idtr.base  = (uint64_t)&idt;
    lidt(&idtr);
    KLOG_DEBUG("idt: %d vectors installed", IDT_ENTRIES);
}
