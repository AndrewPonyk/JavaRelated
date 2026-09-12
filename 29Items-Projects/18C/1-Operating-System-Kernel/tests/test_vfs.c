/*
 * test_vfs.c — Host integration tests for the VFS + ramfs filesystem.
 *
 * Compiles vfs.c + ramfs.c on the host and drives the main file flow plus the
 * error scenarios: missing file, double close, bad fd, fd-table exhaustion,
 * ramfs slot capacity, and per-file size capping.
 */
#include "test_framework.h"
#include "../kernel/include/vfs.h"
#include <string.h>

/* Host stubs for kernel logging used inside vfs.c / ramfs.c. */
int  kprintf(const char *fmt, ...) { (void)fmt; return 0; }
void klog(int level, const char *fmt, ...) { (void)level; (void)fmt; }

static void reset(void)
{
    vfs_init();
    vfs_mount_ramfs();
}

TEST(create_write_read_roundtrip)
{
    reset();
    int fd = vfs_open("a.txt", O_WRONLY | O_CREAT);
    ASSERT_TRUE(fd >= 0);
    ASSERT_EQ(vfs_write(fd, "hello world", 11), 11);
    ASSERT_EQ(vfs_close(fd), 0);

    fd = vfs_open("a.txt", O_RDONLY);
    ASSERT_TRUE(fd >= 0);
    char buf[32];
    ssize_t r = vfs_read(fd, buf, sizeof(buf));
    ASSERT_EQ(r, 11);
    buf[r] = '\0';
    ASSERT_TRUE(strcmp(buf, "hello world") == 0);
    vfs_close(fd);
}

TEST(open_missing_returns_error)
{
    reset();
    ASSERT_TRUE(vfs_open("nope.txt", O_RDONLY) < 0);
}

TEST(double_close_rejected)
{
    reset();
    int fd = vfs_open("b.txt", O_WRONLY | O_CREAT);
    ASSERT_TRUE(fd >= 0);
    ASSERT_EQ(vfs_close(fd), 0);
    ASSERT_TRUE(vfs_close(fd) < 0);          /* already closed */
}

TEST(bad_fd_rejected)
{
    reset();
    char buf[4];
    ASSERT_TRUE(vfs_read(999, buf, 4) < 0);
    ASSERT_TRUE(vfs_read(-1, buf, 4) < 0);
    ASSERT_TRUE(vfs_close(123) < 0);
}

TEST(fd_table_exhaustion)
{
    reset();
    int fd = vfs_open("shared.txt", O_WRONLY | O_CREAT);
    ASSERT_TRUE(fd >= 0);
    vfs_close(fd);

    /* Open the same file repeatedly (no closes) until the fd table is full. */
    int opened = 0, last = 0;
    for (int i = 0; i < 64; i++) {
        last = vfs_open("shared.txt", O_RDONLY);
        if (last >= 0) opened++;
        else break;
    }
    ASSERT_TRUE(opened > 0);
    ASSERT_TRUE(last < 0);                    /* eventually exhausted */
}

TEST(ramfs_slot_capacity)
{
    reset();
    int created = 0;
    for (int i = 0; i < 64; i++) {
        char name[16];
        name[0] = 'f';
        name[1] = (char)('0' + (i / 10));
        name[2] = (char)('0' + (i % 10));
        name[3] = '\0';
        int fd = vfs_open(name, O_WRONLY | O_CREAT);
        if (fd < 0) break;
        created++;
        vfs_close(fd);
    }
    ASSERT_TRUE(created > 0);
    ASSERT_TRUE(created <= 16);                /* bounded by ramfs file slots */
}

TEST(write_capped_at_file_capacity)
{
    reset();
    int fd = vfs_open("big.bin", O_WRONLY | O_CREAT);
    ASSERT_TRUE(fd >= 0);
    static char big[8192];
    memset(big, 'x', sizeof(big));
    ssize_t w = vfs_write(fd, big, sizeof(big));
    ASSERT_TRUE(w > 0 && w <= 4096);          /* ramfs caps each file at 4 KiB */
    vfs_close(fd);
}

int main(void)
{
    printf("vfs/ramfs tests:\n");
    RUN(create_write_read_roundtrip);
    RUN(open_missing_returns_error);
    RUN(double_close_rejected);
    RUN(bad_fd_rejected);
    RUN(fd_table_exhaustion);
    RUN(ramfs_slot_capacity);
    RUN(write_capped_at_file_capacity);
    return test_summary();
}
