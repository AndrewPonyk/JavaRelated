# ============================================================================
#  Memory Allocator — Makefile
#  Targets: all debug release asan test bench demo valgrind format lint clean
#
#  NOTE: object files are shared across build modes. Run `make clean` when
#  switching between debug / release / asan to avoid stale-flag objects.
# ============================================================================

CC      ?= gcc
CSTD    := -std=c11
WARN    := -Wall -Wextra -Wshadow -Wpointer-arith -Wwrite-strings
INCLUDE := -Iinclude -Isrc
CFLAGS  ?= $(CSTD) $(WARN) $(INCLUDE)
LDFLAGS ?=

BUILD := build
OBJ   := $(BUILD)/obj
BIN   := $(BUILD)/bin

# Library sources: everything under src/
LIB_SRC := $(shell find src -name '*.c')
LIB_OBJ := $(patsubst src/%.c,$(OBJ)/%.o,$(LIB_SRC))

# One test executable per tests/test_*.c
TEST_SRC := $(wildcard tests/test_*.c)
TEST_BIN := $(patsubst tests/%.c,$(BIN)/%,$(TEST_SRC))

# Files subject to formatting / linting
ALL_SRC := $(shell find src include tests examples benchmarks -type f \
             \( -name '*.c' -o -name '*.h' \))

.PHONY: all
all: release

# ---- build modes ----------------------------------------------------------
.PHONY: debug release
debug:   CFLAGS += -O0 -g3 -DMEM_DEBUG
debug:   $(BIN)/demo $(TEST_BIN)

release: CFLAGS += -O2 -DNDEBUG
release: $(BIN)/demo

# AddressSanitizer + UBSan build, then run the tests under it.
.PHONY: asan
asan: CFLAGS  += -O1 -g -fsanitize=address,undefined -fno-omit-frame-pointer -DMEM_DEBUG
asan: LDFLAGS += -fsanitize=address,undefined
asan: $(TEST_BIN)
	@fail=0; for t in $(TEST_BIN); do echo "== $$t"; "$$t" || fail=1; done; exit $$fail

# ---- link rules -----------------------------------------------------------
$(BIN)/demo: examples/demo.c $(LIB_OBJ) | $(BIN)
	$(CC) $(CFLAGS) $^ -o $@ $(LDFLAGS)

$(BIN)/bench_strategies: benchmarks/bench_strategies.c $(LIB_OBJ) | $(BIN)
	$(CC) $(CFLAGS) $^ -o $@ $(LDFLAGS)

$(BIN)/test_%: tests/test_%.c $(LIB_OBJ) | $(BIN)
	$(CC) $(CFLAGS) -Itests $^ -o $@ $(LDFLAGS)

# ---- object rule ----------------------------------------------------------
$(OBJ)/%.o: src/%.c | $(OBJ)
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) -c $< -o $@

# ---- run helpers ----------------------------------------------------------
.PHONY: test
test: $(TEST_BIN)
	@fail=0; for t in $(TEST_BIN); do \
		echo "== $$t"; "$$t" || fail=1; done; \
	if [ $$fail -ne 0 ]; then echo "TESTS FAILED"; exit 1; fi; \
	echo "ALL TESTS PASSED"

.PHONY: bench
bench: CFLAGS += -O2 -DNDEBUG
bench: $(BIN)/bench_strategies
	$(BIN)/bench_strategies

.PHONY: demo
demo: $(BIN)/demo
	$(BIN)/demo

.PHONY: valgrind
valgrind: debug
	bash scripts/run_valgrind.sh $(BIN)/demo

# ---- quality --------------------------------------------------------------
.PHONY: format
format:
	clang-format -i $(ALL_SRC)

.PHONY: format-check
format-check:
	clang-format --dry-run --Werror $(ALL_SRC)

.PHONY: lint
lint:
	clang-tidy $(LIB_SRC) -- $(CSTD) $(INCLUDE)

# ---- dirs / clean ---------------------------------------------------------
$(BUILD) $(OBJ) $(BIN):
	@mkdir -p $@

.PHONY: clean
clean:
	$(RM) -r $(BUILD)
