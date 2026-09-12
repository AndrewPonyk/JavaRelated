/*
 * multiboot.c — Walk the Multiboot2 tag list and size physical memory.
 */
#include "include/kernel.h"
#include "include/multiboot.h"

bool mb_parse_memory(uint64_t mb_info, uintptr_t *base, size_t *len)
{
    if (mb_info == 0) return false;

    /* The info block starts with total_size (u32) + reserved (u32). */
    uint8_t *p   = (uint8_t *)(uintptr_t)mb_info;
    uint32_t total = *(uint32_t *)p;
    uint8_t *end = p + total;
    p += 8;

    uint64_t best_base = 0, best_len = 0;

    while (p < end) {
        struct mb2_tag *tag = (struct mb2_tag *)p;
        if (tag->type == MB2_TAG_END) break;

        if (tag->type == MB2_TAG_MMAP) {
            struct mb2_tag_mmap *mm = (struct mb2_tag_mmap *)p;
            uint8_t *e   = p + tag->size;
            uint8_t *cur = p + sizeof(*mm);
            for (; cur < e; cur += mm->entry_size) {
                struct mb2_mmap_entry *me = (struct mb2_mmap_entry *)cur;
                if (me->type == MB2_MMAP_AVAIL && me->length > best_len) {
                    best_base = me->base_addr;
                    best_len  = me->length;
                }
            }
        }

        /* Tags are padded to 8-byte alignment. */
        p += (tag->size + 7) & ~7u;
    }

    if (best_len == 0) return false;
    *base = (uintptr_t)best_base;
    *len  = (size_t)best_len;
    return true;
}
