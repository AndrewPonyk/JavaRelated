# Makefile — Custom CPU Emulator
#
# Common targets:
#   make            build the emulator (build/cpuemu); GUI auto-detected via SDL2
#   make core       force a headless build (no SDL2)
#   make test       build & run the unit + integration suites
#   make fixtures   assemble the NASM guest programs (tests/programs/*.bin)
#   make selftest   run the built-in self-test program
#   make run        build and run --selftest
#   make debug      launch the emulator under gdb on add.bin
#   make format     apply clang-format ; make format-check verifies it
#   make lint       run clang-tidy over the library sources
#   make clean      remove build artifacts
#
# Toggles:  GUI=1|0  ASM_ALU=1  OPT=-O0  CC=clang
# ---------------------------------------------------------------------------

CC      ?= cc
NASM    ?= nasm
PREFIX  ?= /usr/local
OPT     ?= -O2

BUILD   := build
CSTD    := -std=c11
INCLUDE := -Isrc
# -Wno-format-truncation: the disassembler intentionally builds strings with
# bounded snprintf; gcc's truncation heuristic is a known false-positive source.
WARN    := -Wall -Wextra -Werror -Wshadow -Wno-format-truncation
CFLAGS  := $(CSTD) $(INCLUDE) $(WARN) $(OPT) -g -MMD -MP
LDLIBS  :=

# ---- optional SDL2 GUI (auto-detected; override with GUI=0/1) --------------
SDL2_CFLAGS := $(shell pkg-config --cflags sdl2 2>/dev/null)
SDL2_LIBS   := $(shell pkg-config --libs   sdl2 2>/dev/null)
ifneq ($(strip $(SDL2_LIBS)),)
  GUI ?= 1
endif
GUI ?= 0
ifeq ($(GUI),1)
  CFLAGS += -DCPUEMU_ENABLE_GUI=1 $(SDL2_CFLAGS)
  LDLIBS += $(SDL2_LIBS)
endif

# ---- optional hand-written NASM ALU path -----------------------------------
ASM_ALU ?= 0
ifeq ($(ASM_ALU),1)
  CFLAGS  += -DCPUEMU_USE_ASM_ALU=1
  ASM_OBJ := $(BUILD)/asm/alu_fast.o
endif

# ---- sources ---------------------------------------------------------------
LIB_SRC := $(wildcard src/common/*.c src/core/*.c src/loader/*.c)
GUI_SRC := src/gui/visualizer.c
MAIN_SRC:= src/main.c

LIB_OBJ := $(patsubst src/%.c,$(BUILD)/%.o,$(LIB_SRC))
GUI_OBJ := $(patsubst src/%.c,$(BUILD)/%.o,$(GUI_SRC))
MAIN_OBJ:= $(patsubst src/%.c,$(BUILD)/%.o,$(MAIN_SRC))
ALL_OBJ := $(LIB_OBJ) $(GUI_OBJ) $(MAIN_OBJ) $(ASM_OBJ)

TEST_SRC := $(wildcard tests/unit/*.c tests/integration/*.c)
TEST_BIN := $(patsubst tests/%.c,$(BUILD)/tests/%,$(TEST_SRC))

FIXTURES := $(patsubst %.asm,%.bin,$(wildcard tests/programs/*.asm))

BIN := $(BUILD)/cpuemu

.PHONY: all core test fixtures run selftest debug format format-check lint \
        install clean help
.DEFAULT_GOAL := all

all: $(BIN)
	@echo "built $(BIN) (GUI=$(GUI), ASM_ALU=$(ASM_ALU))"

# Force a headless build regardless of SDL2 availability.
core:
	$(MAKE) GUI=0 $(BIN)

$(BIN): $(ALL_OBJ)
	$(CC) $(CFLAGS) $^ -o $@ $(LDLIBS)

# Generic compile rule (mirrors src/ tree under build/).
$(BUILD)/%.o: src/%.c
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) -c $< -o $@

# NASM helper.
$(BUILD)/asm/%.o: src/asm/%.asm
	@mkdir -p $(dir $@)
	$(NASM) -f elf64 $< -o $@

# ---- tests: each test links against the library objects --------------------
test: $(TEST_BIN)
	@echo "── running tests ──────────────────────────────"; \
	fail=0; for t in $(TEST_BIN); do \
	  echo "▶ $$t"; ./$$t || fail=1; \
	done; \
	if [ $$fail -eq 0 ]; then echo "ALL TESTS PASSED"; else echo "TESTS FAILED"; fi; \
	exit $$fail

$(BUILD)/tests/%: tests/%.c $(LIB_OBJ)
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) -Itests $< $(LIB_OBJ) -o $@ $(LDLIBS)

# ---- NASM guest fixtures ---------------------------------------------------
fixtures: $(FIXTURES)
tests/programs/%.bin: tests/programs/%.asm
	$(NASM) -f bin $< -o $@

# ---- convenience -----------------------------------------------------------
run selftest: $(BIN)
	./$(BIN) --selftest --headless

debug: $(BIN) fixtures
	gdb --args ./$(BIN) --program tests/programs/add.bin --headless

format:
	@clang-format -i $$(find src tests -name '*.c' -o -name '*.h')
format-check:
	@clang-format --dry-run -Werror $$(find src tests -name '*.c' -o -name '*.h')

lint:
	@clang-tidy $(LIB_SRC) -- $(CFLAGS)

install: $(BIN)
	install -d $(DESTDIR)$(PREFIX)/bin
	install -m 0755 $(BIN) $(DESTDIR)$(PREFIX)/bin/cpuemu

clean:
	$(RM) -r $(BUILD) $(FIXTURES)

help:
	@grep -E '^#   make' Makefile | sed 's/^#   //'

# Auto-generated header dependencies.
-include $(ALL_OBJ:.o=.d)
