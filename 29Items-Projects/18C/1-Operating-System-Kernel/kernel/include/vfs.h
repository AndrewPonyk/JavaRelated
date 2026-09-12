/*
 * vfs.h — Virtual Filesystem layer.
 *
 * A thin abstraction so the syscall layer talks to "files" without knowing the
 * backend. ramfs is the first (in-memory) backend.
 */
#ifndef KERNEL_VFS_H
#define KERNEL_VFS_H

#include "types.h"

#define VFS_NAME_MAX     64
#define VFS_MAX_OPEN     32

/* Standard stream descriptors (mirrored in syscall.h). */
#ifndef STDIN_FILENO
#define STDIN_FILENO   0
#define STDOUT_FILENO  1
#define STDERR_FILENO  2
#endif

/* open() flags */
#define O_RDONLY  0x0
#define O_WRONLY  0x1
#define O_RDWR    0x2
#define O_CREAT   0x100

typedef enum { VN_FILE, VN_DIR } vnode_type_t;

/* In-core node. Backends fill in `ops` and `priv`. */
typedef struct vnode {
    char           name[VFS_NAME_MAX];
    vnode_type_t   type;
    size_t         size;
    void          *priv;                 /* backend-private data */
    struct vfs_ops *ops;
} vnode_t;

/* Backend operation table (the polymorphism seam). */
typedef struct vfs_ops {
    int     (*open)(vnode_t *node, int flags);
    int     (*close)(vnode_t *node);
    ssize_t (*read)(vnode_t *node, size_t off, void *buf, size_t n);
    ssize_t (*write)(vnode_t *node, size_t off, const void *buf, size_t n);
} vfs_ops_t;

/* ---- VFS public API (used by syscall layer) --------------------- */
void    vfs_init(void);
int     vfs_open(const char *path, int flags);          /* returns fd or -errno */
int     vfs_close(int fd);
ssize_t vfs_read(int fd, void *buf, size_t n);
ssize_t vfs_write(int fd, const void *buf, size_t n);

/* Mount the ramfs backend at root. */
void    vfs_mount_ramfs(void);

/* List ramfs file names; returns count written into `names`. */
int     ramfs_list(char names[][VFS_NAME_MAX], int max);

#endif /* KERNEL_VFS_H */
