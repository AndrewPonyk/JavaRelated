/*
 * multiboot.h — Minimal Multiboot2 information parsing.
 *
 * GRUB hands the kernel a pointer to a tag list. We only need the memory map
 * (tag type 6) to size physical memory for the PMM.
 */
#ifndef KERNEL_MULTIBOOT_H
#define KERNEL_MULTIBOOT_H

#include "types.h"

#define MB2_TAG_END      0
#define MB2_TAG_MMAP     6
#define MB2_MMAP_AVAIL   1

struct mb2_tag {
    uint32_t type;
    uint32_t size;
} __attribute__((packed));

struct mb2_mmap_entry {
    uint64_t base_addr;
    uint64_t length;
    uint32_t type;
    uint32_t reserved;
} __attribute__((packed));

struct mb2_tag_mmap {
    uint32_t type;
    uint32_t size;
    uint32_t entry_size;
    uint32_t entry_version;
    /* entries follow */
} __attribute__((packed));

/* Find the largest available RAM region. Returns true and fills the base/len
 * out-parameters on success, false if no memory map tag was present. */
bool mb_parse_memory(uint64_t mb_info, uintptr_t *base, size_t *len);

#endif /* KERNEL_MULTIBOOT_H */
