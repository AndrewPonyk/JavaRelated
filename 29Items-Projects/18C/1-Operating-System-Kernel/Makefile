# Makefile — Operating System Kernel
#
# Two toolchains live here (see TECH-NOTES §3.1):
#   * CROSS build  -> freestanding kernel.elf for QEMU/bare metal
#   * HOST  build  -> unit tests compiled with the host gcc + libc
#
# Common targets:
#   make            build kernel.elf
#   make iso        build a bootable GRUB ISO
#   make run        boot kernel.elf in QEMU (serial -> stdio)
#   make debug      boot under QEMU stopped, ready for GDB
#   make test       build & run host unit tests
#   make lint       clang-format check
#   make clean

# Optionally load local config (BUILD / QEMU_MEM / EXTRA_DEFINES). The leading
# '-' makes it non-fatal when .env is absent. Comes before the ?= defaults so a
# value set in .env takes precedence.
-include .env

# ---- Toolchain --------------------------------------------------------------
CC      := gcc
LD      := ld
ASM     := nasm
QEMU    := qemu-system-x86_64

BUILD    ?= debug                # debug | relwd | rel  (see .env.example)
QEMU_MEM ?= 128M                 # guest RAM for `make run`

# ---- Directories ------------------------------------------------------------
BUILDDIR := build
KDIR     := kernel
BOOTDIR  := boot

# ---- Flags ------------------------------------------------------------------
# Freestanding kernel flags: no libc, no red zone, kernel code model.
CFLAGS_COMMON := -std=c17 -Wall -Wextra -Werror -ffreestanding -fno-stack-protector \
                -fno-pic -mno-red-zone -mno-mmx -mno-sse -mno-sse2 \
                -mcmodel=kernel -Ikernel/include

ifeq ($(BUILD),debug)
CFLAGS_COMMON += -O0 -g -DDEBUG -DCONFIG_SELFTEST
else ifeq ($(BUILD),relwd)
CFLAGS_COMMON += -O2 -g -DNDEBUG
else
CFLAGS_COMMON += -O2 -DNDEBUG
endif

# Extra -D defines (e.g. -DCONFIG_INTERACTIVE for the live shell).
CFLAGS_COMMON += $(EXTRA_DEFINES)

LDFLAGS := -n -T $(BOOTDIR)/linker.ld -nostdlib
ASFLAGS := -f elf64

# ---- Sources ----------------------------------------------------------------
C_SRC := $(wildcard $(KDIR)/*.c) \
         $(wildcard $(KDIR)/mm/*.c) \
         $(wildcard $(KDIR)/sched/*.c) \
         $(wildcard $(KDIR)/syscall/*.c) \
         $(wildcard $(KDIR)/fs/*.c) \
         $(wildcard $(KDIR)/drivers/*.c) \
         $(wildcard $(KDIR)/shell/*.c) \
         $(wildcard $(KDIR)/arch/x86_64/*.c) \
         $(wildcard $(KDIR)/lib/*.c)

ASM_SRC := $(BOOTDIR)/boot.asm \
           $(wildcard $(KDIR)/arch/x86_64/*.asm)

C_OBJ   := $(patsubst %.c,$(BUILDDIR)/%.o,$(C_SRC))
ASM_OBJ := $(patsubst %.asm,$(BUILDDIR)/%.o,$(ASM_SRC))
OBJ     := $(ASM_OBJ) $(C_OBJ)

KERNEL  := $(BUILDDIR)/kernel.elf
ISO     := $(BUILDDIR)/os.iso

# ---- Default: build the kernel ---------------------------------------------
.PHONY: all
all: $(KERNEL)

$(BUILDDIR)/%.o: %.c
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS_COMMON) -c $< -o $@

$(BUILDDIR)/%.o: %.asm
	@mkdir -p $(dir $@)
	$(ASM) $(ASFLAGS) $< -o $@

$(KERNEL): $(OBJ) $(BOOTDIR)/linker.ld
	@mkdir -p $(dir $@)
	$(LD) $(LDFLAGS) -o $@ $(OBJ)
	@echo "built $@ ($(BUILD))"

# ---- Bootable ISO (GRUB) ----------------------------------------------------
.PHONY: iso
iso: $(ISO)
$(ISO): $(KERNEL) config/grub.cfg
	@mkdir -p $(BUILDDIR)/isodir/boot/grub
	cp $(KERNEL) $(BUILDDIR)/isodir/boot/kernel.elf
	cp config/grub.cfg $(BUILDDIR)/isodir/boot/grub/grub.cfg
	grub-mkrescue -o $@ $(BUILDDIR)/isodir

# ---- Run / debug in QEMU ----------------------------------------------------
# Multiboot2 + a higher-half (>4 GiB entry) kernel must be booted by GRUB, so we
# boot the ISO with -cdrom rather than QEMU's Multiboot1 `-kernel` loader.
.PHONY: run
run: $(ISO)
	$(QEMU) -cdrom $(ISO) -serial stdio -display none -no-reboot -m $(QEMU_MEM)

.PHONY: run-graphic
run-graphic: $(ISO)
	$(QEMU) -cdrom $(ISO) -serial stdio -m $(QEMU_MEM)

# Boot into the live interactive shell (rebuilds with CONFIG_INTERACTIVE).
.PHONY: run-shell
run-shell:
	$(MAKE) clean
	$(MAKE) run-graphic EXTRA_DEFINES=-DCONFIG_INTERACTIVE

.PHONY: debug
debug: $(ISO)
	$(QEMU) -cdrom $(ISO) -serial stdio -display none -no-reboot -d int,cpu_reset -s -S &
	gdb -ex "target remote :1234" -ex "symbol-file $(KERNEL)"

# ---- Host unit tests --------------------------------------------------------
HOST_CFLAGS := -std=c17 -Wall -Wextra -g -O0 -Ikernel/include
TESTBIN     := $(BUILDDIR)/tests

.PHONY: test
test:
	@mkdir -p $(TESTBIN)
	$(CC) $(HOST_CFLAGS) tests/test_pmm.c         kernel/mm/pmm.c            -o $(TESTBIN)/test_pmm
	$(CC) $(HOST_CFLAGS) tests/test_kheap.c       kernel/mm/kheap.c          -o $(TESTBIN)/test_kheap
	$(CC) $(HOST_CFLAGS) tests/test_ml_priority.c kernel/sched/ml_priority.c -o $(TESTBIN)/test_ml
	$(CC) $(HOST_CFLAGS) tests/test_vfs.c         kernel/fs/vfs.c kernel/fs/ramfs.c -o $(TESTBIN)/test_vfs
	$(CC) $(HOST_CFLAGS) -DKERNEL_HOSTTEST tests/test_scheduler.c kernel/sched/scheduler.c \
	      kernel/sched/ml_priority.c -o $(TESTBIN)/test_sched
	@echo "== running unit tests =="
	@$(TESTBIN)/test_pmm
	@$(TESTBIN)/test_kheap
	@$(TESTBIN)/test_ml
	@$(TESTBIN)/test_vfs
	@$(TESTBIN)/test_sched

# ---- Lint -------------------------------------------------------------------
.PHONY: lint
lint:
	clang-format --dry-run -Werror $(C_SRC) $(wildcard kernel/include/*.h)

.PHONY: format
format:
	clang-format -i $(C_SRC) $(wildcard kernel/include/*.h)

# ---- Clean ------------------------------------------------------------------
.PHONY: clean
clean:
	rm -rf $(BUILDDIR)
