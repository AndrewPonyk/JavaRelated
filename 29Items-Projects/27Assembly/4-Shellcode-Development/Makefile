# Makefile — assemble shellcode, extract raw bytes, build the vulnerable target, test.
# Toolchain: nasm + ld (shellcode) and gcc (vuln target). Wildcard-driven.

ASM      := nasm
ASMFLAGS := -f elf64
LD       := ld
CC       ?= cc
PYTHON   := python3

SRC_DIR := src/shellcode
TGT_DIR := src/target
BIN_DIR := bin

SOURCES := $(wildcard $(SRC_DIR)/*.asm)
OBJECTS := $(SOURCES:$(SRC_DIR)/%.asm=$(BIN_DIR)/%.o)
ELFS    := $(OBJECTS:%.o=%)
RAWS    := $(ELFS:%=%.bin)

CFLAGS_VULN := -O0 -fno-omit-frame-pointer -fno-stack-protector -fno-pic \
               -no-pie -z execstack

.PHONY: all payloads extract vuln test cov lint check clean help

all: $(ELFS)

payloads: all extract

# Assemble .asm -> .o
$(BIN_DIR)/%.o: $(SRC_DIR)/%.asm | $(BIN_DIR)
	$(ASM) $(ASMFLAGS) -o $@ $<

# Link .o -> ELF executable
$(BIN_DIR)/%: $(BIN_DIR)/%.o | $(BIN_DIR)
	$(LD) -o $@ $<

# Extract raw position-independent shellcode (.text only) -> .bin
$(BIN_DIR)/%.bin: $(BIN_DIR)/% | $(BIN_DIR)
	objcopy -O binary --only-section=.text $< $@
	@echo "Raw shellcode -> $@ ($$(wc -c < $@) bytes)"

extract: $(RAWS)

# Vulnerable teaching target (stack overflow, execstack, no-PIE)
vuln: $(BIN_DIR)/vuln

$(BIN_DIR)/vuln: $(TGT_DIR)/vuln.c | $(BIN_DIR)
	$(CC) $(CFLAGS_VULN) -o $@ $<
	@echo "Built vulnerable target -> $@"

$(BIN_DIR):
	mkdir -p $(BIN_DIR)

# Run the pytest suite
test:
	$(PYTHON) -m pytest tests/ -v

# Run the suite with coverage (fails below 80%, see pyproject.toml)
cov:
	$(PYTHON) -m pytest tests/ -v --cov --cov-report=term-missing

# Lint the Python scripts/tests (ruff)
lint:
	ruff check src/ tests/

# lint + tests + coverage
check: lint cov

clean:
	rm -rf $(BIN_DIR)

help:
	@echo "make all       assemble + link shellcode ELF binaries"
	@echo "make extract   objcopy raw .text -> bin/*.bin"
	@echo "make vuln      build the deliberately-vulnerable target (bin/vuln)"
	@echo "make payloads  all + extract"
	@echo "make test      run the pytest suite"
	@echo "make cov       run tests with coverage (fails < 80%)"
	@echo "make lint      lint Python (ruff)"
	@echo "make check     lint + coverage"
	@echo "make clean     remove bin/"
