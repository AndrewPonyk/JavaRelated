/*
 * selftest.c — In-kernel self tests + subsystem demo.
 *
 * Runs at boot (gated by CONFIG_SELFTEST) from the init thread, exercising every
 * major subsystem against real hardware state and printing PASS/FAIL over the
 * serial console so CI can assert on it. This is the in-QEMU complement to the
 * host unit tests in tests/.
 */
#include "include/kernel.h"
#include "include/memory.h"
#include "include/sched.h"
#include "include/ml_sched.h"
#include "include/vfs.h"
#include "include/syscall.h"

extern int64_t syscall_dispatch(syscall_frame_t *f);

static int g_pass, g_fail;

#define CHECK(desc, cond)                                   \
    do {                                                    \
        if (cond) { g_pass++; kprintf("  [ ok ] %s\n", desc); } \
        else      { g_fail++; kprintf("  [FAIL] %s\n", desc); } \
    } while (0)

static void test_pmm(void)
{
    kprintf("pmm:\n");
    size_t before = pmm_free_count();
    uintptr_t a = pmm_alloc_frame();
    uintptr_t b = pmm_alloc_frame();
    CHECK("alloc returns nonzero, aligned", a && b && (a % PAGE_SIZE) == 0);
    CHECK("frames are distinct", a != b);
    CHECK("free count decreased by 2", pmm_free_count() == before - 2);
    pmm_free_frame(a);
    pmm_free_frame(b);
    CHECK("free restores count", pmm_free_count() == before);
}

static void test_heap(void)
{
    kprintf("kheap:\n");
    void *p = kmalloc(128);
    void *q = kmalloc(4096);
    CHECK("kmalloc nonnull, distinct", p && q && p != q);
    /* write/read pattern */
    uint8_t *bytes = (uint8_t *)p;
    for (int i = 0; i < 128; i++) bytes[i] = (uint8_t)i;
    int ok = 1;
    for (int i = 0; i < 128; i++) if (bytes[i] != (uint8_t)i) ok = 0;
    CHECK("heap memory holds data", ok);
    kfree(p);
    kfree(q);
    void *r = kcalloc(64, 1);
    int zero = 1;
    for (int i = 0; i < 64; i++) if (((uint8_t *)r)[i] != 0) zero = 0;
    CHECK("kcalloc zeroes memory", zero);
    kfree(r);
}

static void test_vmm(void)
{
    kprintf("vmm:\n");
    const uintptr_t va = 0xFFFFFFFFC0000000UL;   /* a free higher-half slot */
    uintptr_t frame = pmm_alloc_frame();
    CHECK("got a frame to map", frame != 0);

    int rc = vmm_map(NULL, va, frame, PTE_WRITE);
    CHECK("vmm_map succeeds", rc == EOK);
    CHECK("vmm_translate resolves mapping", vmm_translate(NULL, va) == frame);

    volatile uint64_t *ptr = (volatile uint64_t *)va;
    *ptr = 0xDEADBEEFCAFEUL;
    CHECK("write/read through new mapping", *ptr == 0xDEADBEEFCAFEUL);

    rc = vmm_unmap(NULL, va);
    CHECK("vmm_unmap succeeds", rc == EOK);
    CHECK("translate fails after unmap", vmm_translate(NULL, va) == 0);
    pmm_free_frame(frame);
}

static void test_ramfs(void)
{
    kprintf("vfs/ramfs:\n");
    int fd = vfs_open("notes.txt", O_WRONLY | O_CREAT);
    CHECK("open O_CREAT", fd >= 0);
    ssize_t w = vfs_write(fd, "kernel data", 11);
    CHECK("write returns byte count", w == 11);
    vfs_close(fd);

    fd = vfs_open("notes.txt", O_RDONLY);
    CHECK("reopen existing file", fd >= 0);
    char buf[16];
    ssize_t r = vfs_read(fd, buf, sizeof(buf));
    CHECK("read returns byte count", r == 11);
    buf[r > 0 ? r : 0] = '\0';
    CHECK("contents round-trip", strcmp(buf, "kernel data") == 0);
    vfs_close(fd);

    CHECK("open missing file fails", vfs_open("nope.txt", O_RDONLY) < 0);
}

static void test_ml(void)
{
    kprintf("ml priority:\n");
    ml_model_t m;
    ml_model_init(&m);

    sched_features_t cpu = { .cpu_burst_ema = 400, .io_wait_ema = 0,
                             .age_ticks = 0, .nice = 0 };
    sched_features_t io  = { .cpu_burst_ema = 0, .io_wait_ema = 400,
                             .age_ticks = 0, .nice = 0 };
    CHECK("score in range", ml_priority_score(&m, &cpu) < SCHED_NPRIO);
    CHECK("io-bound >= priority of cpu-bound",
          ml_priority_score(&m, &io) <= ml_priority_score(&m, &cpu));

    uint8_t before = ml_priority_score(&m, &cpu);
    for (int i = 0; i < 50; i++) ml_model_update(&m, &cpu, 0);
    CHECK("online update converges toward target",
          ml_priority_score(&m, &cpu) <= before);
}

static void test_syscall(void)
{
    kprintf("syscall ABI:\n");
    /* getpid through the dispatcher (we run inside the init thread). */
    syscall_frame_t f = { .num = SYS_getpid };
    int64_t pid = syscall_dispatch(&f);
    CHECK("SYS_getpid returns current pid",
          pid == (sched_current() ? sched_current()->pid : -1));

    /* SYS_write of a buffer in identity-mapped low memory (a valid "user"
     * pointer per the boundary check) to STDOUT. */
    uintptr_t frame = pmm_alloc_frame();
    const char *msg = "    (hello from the syscall path)\n";
    size_t n = strlen(msg);
    memcpy((void *)frame, msg, n);
    syscall_frame_t w = { .num = SYS_write, .arg0 = STDOUT_FILENO,
                          .arg1 = frame, .arg2 = n };
    CHECK("SYS_write returns byte count", syscall_dispatch(&w) == (int64_t)n);

    /* Rejecting a kernel pointer proves the boundary validation works. */
    syscall_frame_t bad = { .num = SYS_write, .arg0 = STDOUT_FILENO,
                            .arg1 = KERNEL_VBASE + 0x1000, .arg2 = 8 };
    CHECK("SYS_write rejects kernel pointer", syscall_dispatch(&bad) < 0);
    pmm_free_frame(frame);
}

int selftest_run(void)
{
    g_pass = g_fail = 0;
    kprintf("\n==================== KERNEL SELF-TESTS ====================\n");
    test_pmm();
    test_heap();
    test_vmm();
    test_ramfs();
    test_ml();
    test_syscall();
    kprintf("----------------------------------------------------------\n");
    kprintf("SELFTEST: %d passed, %d failed\n", g_pass, g_fail);
    if (g_fail == 0) kprintf("SELFTEST: ALL PASSED\n");
    kprintf("==========================================================\n\n");
    return g_fail;
}
