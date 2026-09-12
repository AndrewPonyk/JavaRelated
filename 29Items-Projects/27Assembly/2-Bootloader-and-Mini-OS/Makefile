# =============================================================================
#  Makefile  --  Single source of truth for the Mini-OS build
#
#  Targets:
#    make           build the bootable image (build/os-image.bin)
#    make run       build + boot it in QEMU
#    make debug     boot frozen, waiting for gdb on :1234
#    make test      compile + run the host unit tests (no QEMU)
#    make lint      clang-format --dry-run check on the C sources
#    make clean     remove build artifacts
#
#  Toolchain: defaults to host gcc with -m32. For a cleaner bare-metal build
#  use a cross-compiler:  make CROSS=i686-elf-
# =============================================================================

# ---- toolchain ----
CROSS   ?=
CC      := $(CROSS)gcc
LD      := $(CROSS)ld
OBJCOPY := $(CROSS)objcopy
NASM    ?= nasm
QEMU    ?= qemu-system-i386
HOSTCC  ?= gcc

# ---- run knobs (overridable; mirror .env.example) ----
QEMU_MEM ?= 64M

# ---- directories ----
BUILD := build
INCLUDE := kernel/include

# ---- flags ----
# -ffreestanding: no hosted libc assumptions. -fno-pie/-no-pie: fixed load addr.
# -fno-stack-protector: no canary calls we can't link. -m32: 32-bit code.
CFLAGS  := -m32 -ffreestanding -fno-pie -fno-stack-protector \
           -fno-asynchronous-unwind-tables -Wall -Wextra -O2 \
           -I$(INCLUDE) -c
# --no-warn-rwx-segments: a flat kernel intentionally has one code+data segment
# (no per-section paging perms yet); silence the cosmetic warning. Needs
# binutils >= 2.39 (the pinned Docker toolchain has it).
LDFLAGS := -m elf_i386 -nostdlib -T tools/linker.ld --no-warn-rwx-segments
# -fno-builtin: our memset/memcpy must not be turned into calls to themselves
# (a classic freestanding-libc-on-a-hosted-compiler trap).
HOST_CFLAGS := -std=c11 -Wall -Wextra -g -O1 -fno-builtin

# ---- sources / objects ----
C_SOURCES  := $(wildcard kernel/*.c kernel/*/*.c)
ASM_KERNEL := kernel/cpu/idt_load.asm kernel/cpu/isr_stubs.asm kernel/cpu/gdt_flush.asm
ENTRY_OBJ  := $(BUILD)/kernel/kernel_entry.o
OBJ        := $(patsubst %.c,$(BUILD)/%.o,$(C_SOURCES)) \
              $(patsubst %.asm,$(BUILD)/%.o,$(ASM_KERNEL))

.PHONY: all run debug test lint clean help
all: $(BUILD)/os-image.bin

# ---- stage 1: bootloader (flat binary, exactly 512 bytes) ----
$(BUILD)/boot.bin: boot/boot.asm $(wildcard boot/*.asm)
	@mkdir -p $(BUILD)
	$(NASM) -f bin -I. boot/boot.asm -o $@

# ---- kernel entry stub (linked FIRST -> sits at 0x1000) ----
$(ENTRY_OBJ): kernel/kernel_entry.asm
	@mkdir -p $(dir $@)
	$(NASM) -f elf32 $< -o $@

# ---- pattern rules ----
$(BUILD)/%.o: %.c
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) $< -o $@

$(BUILD)/%.o: %.asm
	@mkdir -p $(dir $@)
	$(NASM) -f elf32 $< -o $@

# ---- link kernel -> ELF -> flat binary ----
$(BUILD)/kernel.bin: $(ENTRY_OBJ) $(OBJ)
	$(LD) $(LDFLAGS) -o $(BUILD)/kernel.elf $(ENTRY_OBJ) $(OBJ)
	$(OBJCOPY) -O binary $(BUILD)/kernel.elf $@

# ---- final bootable image ----
$(BUILD)/os-image.bin: $(BUILD)/boot.bin $(BUILD)/kernel.bin
	bash tools/create_image.sh $(BUILD)/boot.bin $(BUILD)/kernel.bin $@

# ---- run / debug ----
run: $(BUILD)/os-image.bin
	$(QEMU) -drive format=raw,file=$< -m $(QEMU_MEM) -no-reboot

debug: $(BUILD)/os-image.bin
	$(QEMU) -drive format=raw,file=$< -m $(QEMU_MEM) -no-reboot -d int,cpu_reset -s -S

# ---- host unit tests (native compiler, no QEMU) ----
test:
	@mkdir -p $(BUILD)
	@echo ">> scheduler"
	@$(HOSTCC) $(HOST_CFLAGS) tests/test_scheduler.c kernel/sched/scheduler.c \
		-o $(BUILD)/test_scheduler && $(BUILD)/test_scheduler
	@echo ">> string / mem"
	@$(HOSTCC) $(HOST_CFLAGS) tests/test_string.c kernel/lib/string.c \
		kernel/lib/itoa.c kernel/mm/memory.c -o $(BUILD)/test_string && $(BUILD)/test_string
	@echo ">> ring buffer"
	@$(HOSTCC) $(HOST_CFLAGS) tests/test_ring_buffer.c \
		-o $(BUILD)/test_ring_buffer && $(BUILD)/test_ring_buffer
	@echo ">> pmm"
	@$(HOSTCC) $(HOST_CFLAGS) tests/test_pmm.c kernel/mm/pmm.c \
		-o $(BUILD)/test_pmm && $(BUILD)/test_pmm
	@echo ">> kprintf"
	@$(HOSTCC) $(HOST_CFLAGS) tests/test_kprintf.c kernel/lib/kprintf.c \
		-o $(BUILD)/test_kprintf && $(BUILD)/test_kprintf
	@echo ">> heap"
	@$(HOSTCC) $(HOST_CFLAGS) tests/test_heap.c kernel/mm/memory.c \
		-o $(BUILD)/test_heap && $(BUILD)/test_heap
	@echo "All host tests passed."

# ---- lint ----
lint:
	@clang-format --dry-run --Werror $(C_SOURCES) $(wildcard $(INCLUDE)/*.h kernel/lib/*.h)

clean:
	rm -rf $(BUILD)

help:
	@echo "make [all] | run | debug | test | lint | clean"
	@echo "  CROSS=i686-elf-   use a cross-compiler"
	@echo "  QEMU_MEM=128M     guest memory size"
