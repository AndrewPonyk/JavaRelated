/*
 * vfs.c — Virtual Filesystem layer (open file table + dispatch to backend).
 */
#include "../include/kernel.h"
#include "../include/vfs.h"

/* ramfs hooks (fs/ramfs.c) */
vnode_t  *ramfs_lookup(const char *path, int flags);
void      ramfs_init(void);

typedef struct open_file {
    vnode_t *node;
    size_t   offset;
    int      flags;
    bool     used;
} open_file_t;

static open_file_t open_table[VFS_MAX_OPEN];

void vfs_init(void)
{
    for (int i = 0; i < VFS_MAX_OPEN; i++) open_table[i].used = false;
    KLOG_INFO("vfs: initialized (%d fd slots)", VFS_MAX_OPEN);
}

void vfs_mount_ramfs(void)
{
    ramfs_init();
    KLOG_INFO("vfs: ramfs mounted at /");
}

static int alloc_fd(void)
{
    for (int i = 3; i < VFS_MAX_OPEN; i++)   /* 0..2 reserved for std streams */
        if (!open_table[i].used) return i;
    return -1;
}

int vfs_open(const char *path, int flags)
{
    vnode_t *node = ramfs_lookup(path, flags);
    if (!node) return -ENOENT;

    int fd = alloc_fd();
    if (fd < 0) return -ENOSPC;

    open_table[fd].node   = node;
    open_table[fd].offset = 0;
    open_table[fd].flags  = flags;
    open_table[fd].used   = true;
    if (node->ops && node->ops->open) node->ops->open(node, flags);
    return fd;
}

int vfs_close(int fd)
{
    if (fd < 0 || fd >= VFS_MAX_OPEN || !open_table[fd].used) return -EINVAL;
    vnode_t *node = open_table[fd].node;
    if (node && node->ops && node->ops->close) node->ops->close(node);
    open_table[fd].used = false;
    return EOK;
}

ssize_t vfs_read(int fd, void *buf, size_t n)
{
    if (fd < 0 || fd >= VFS_MAX_OPEN || !open_table[fd].used) return -EINVAL;
    open_file_t *of = &open_table[fd];
    if (!of->node->ops || !of->node->ops->read) return -EIO;
    ssize_t r = of->node->ops->read(of->node, of->offset, buf, n);
    if (r > 0) of->offset += (size_t)r;
    return r;
}

ssize_t vfs_write(int fd, const void *buf, size_t n)
{
    /* Special-case the console streams so early code can print before any
     * file is opened. */
    if (fd == STDOUT_FILENO || fd == STDERR_FILENO) {
        const char *s = (const char *)buf;
        for (size_t i = 0; i < n; i++) kprintf("%c", s[i]);
        return (ssize_t)n;
    }
    if (fd < 0 || fd >= VFS_MAX_OPEN || !open_table[fd].used) return -EINVAL;
    open_file_t *of = &open_table[fd];
    if (!of->node->ops || !of->node->ops->write) return -EIO;
    ssize_t w = of->node->ops->write(of->node, of->offset, buf, n);
    if (w > 0) of->offset += (size_t)w;
    return w;
}
