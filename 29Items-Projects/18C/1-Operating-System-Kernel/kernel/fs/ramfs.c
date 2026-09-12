/*
 * ramfs.c — In-memory filesystem backend.
 *
 * A flat table of fixed-capacity files. Demonstrates the VFS backend contract;
 * a real impl would add directories, dynamic growth, and an inode allocator.
 */
#include "../include/kernel.h"
#include "../include/vfs.h"

#define RAMFS_MAX_FILES   16
#define RAMFS_FILE_CAP    4096

typedef struct ramfs_file {
    vnode_t  node;
    uint8_t  data[RAMFS_FILE_CAP];
    bool     used;
} ramfs_file_t;

static ramfs_file_t files[RAMFS_MAX_FILES];
static vfs_ops_t     ramfs_ops;   /* forward-declared handlers below */

static int     ramfs_open(vnode_t *n, int flags);
static int     ramfs_close(vnode_t *n);
static ssize_t ramfs_read(vnode_t *n, size_t off, void *buf, size_t n_bytes);
static ssize_t ramfs_write(vnode_t *n, size_t off, const void *buf, size_t n_bytes);

static bool name_eq(const char *a, const char *b)
{
    while (*a && *b) { if (*a != *b) return false; a++; b++; }
    return *a == *b;
}

static void name_copy(char *dst, const char *src)
{
    size_t i = 0;
    /* skip a leading '/' so "/hello" and "hello" match the same file */
    if (src[0] == '/') src++;
    for (; src[i] && i < VFS_NAME_MAX - 1; i++) dst[i] = src[i];
    dst[i] = '\0';
}

void ramfs_init(void)
{
    ramfs_ops.open  = ramfs_open;
    ramfs_ops.close = ramfs_close;
    ramfs_ops.read  = ramfs_read;
    ramfs_ops.write = ramfs_write;
    for (int i = 0; i < RAMFS_MAX_FILES; i++) files[i].used = false;
    KLOG_DEBUG("ramfs: %d file slots, %d bytes each",
               RAMFS_MAX_FILES, RAMFS_FILE_CAP);
}

/* Find an existing file by name, or create it when O_CREAT is set. */
vnode_t *ramfs_lookup(const char *path, int flags)
{
    char want[VFS_NAME_MAX];
    name_copy(want, path);

    for (int i = 0; i < RAMFS_MAX_FILES; i++)
        if (files[i].used && name_eq(files[i].node.name, want))
            return &files[i].node;

    if (!(flags & O_CREAT)) return NULL;

    for (int i = 0; i < RAMFS_MAX_FILES; i++) {
        if (!files[i].used) {
            files[i].used      = true;
            files[i].node.type = VN_FILE;
            files[i].node.size = 0;
            files[i].node.priv = &files[i];
            files[i].node.ops  = &ramfs_ops;
            name_copy(files[i].node.name, path);
            return &files[i].node;
        }
    }
    return NULL; /* table full */
}

/* List current file names; returns the count written into `names`. */
int ramfs_list(char names[][VFS_NAME_MAX], int max)
{
    int n = 0;
    for (int i = 0; i < RAMFS_MAX_FILES && n < max; i++) {
        if (files[i].used) {
            int j = 0;
            for (; files[i].node.name[j] && j < VFS_NAME_MAX - 1; j++)
                names[n][j] = files[i].node.name[j];
            names[n][j] = '\0';
            n++;
        }
    }
    return n;
}

static int ramfs_open(vnode_t *n, int flags)  { UNUSED(n); UNUSED(flags); return EOK; }
static int ramfs_close(vnode_t *n)            { UNUSED(n); return EOK; }

static ssize_t ramfs_read(vnode_t *n, size_t off, void *buf, size_t n_bytes)
{
    ramfs_file_t *f = (ramfs_file_t *)n->priv;
    if (off >= n->size) return 0;                 /* EOF */
    size_t avail = n->size - off;
    if (n_bytes > avail) n_bytes = avail;
    uint8_t *dst = (uint8_t *)buf;
    for (size_t i = 0; i < n_bytes; i++) dst[i] = f->data[off + i];
    return (ssize_t)n_bytes;
}

static ssize_t ramfs_write(vnode_t *n, size_t off, const void *buf, size_t n_bytes)
{
    ramfs_file_t *f = (ramfs_file_t *)n->priv;
    if (off >= RAMFS_FILE_CAP) return -ENOSPC;
    if (off + n_bytes > RAMFS_FILE_CAP) n_bytes = RAMFS_FILE_CAP - off;
    const uint8_t *src = (const uint8_t *)buf;
    for (size_t i = 0; i < n_bytes; i++) f->data[off + i] = src[i];
    if (off + n_bytes > n->size) n->size = off + n_bytes;
    return (ssize_t)n_bytes;
}
