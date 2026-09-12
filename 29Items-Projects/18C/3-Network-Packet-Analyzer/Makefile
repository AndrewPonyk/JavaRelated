# Network Packet Analyzer (npa) — build orchestration
#
# Targets:
#   make            build the application binary (build/bin/npa)
#   make test       build + run the unit test suite
#   make lint       clang-tidy + cppcheck (scripts/lint.sh)
#   make format     apply clang-format     (scripts/format.sh)
#   make coverage   build tests with gcov and emit a coverage summary
#   make run        build, then run with ARGS (e.g. make run ARGS="-r x.pcap")
#   make install    install the binary to PREFIX (default /usr/local)
#   make clean      remove build artifacts
#
# Switches:
#   STRICT=1   treat warnings as errors (CI uses this)
#   ASAN=1     build with AddressSanitizer + UBSan
#   OPT=-O3    override optimization level
#   CC=clang   choose compiler
#
# The application links libpcap + ncursesw; the test binaries deliberately do
# NOT (they exercise the pure ring/decode/anomaly modules), so `make test`
# works even on a box without those -dev packages.

CC      ?= cc
OPT     ?= -O2
PREFIX  ?= /usr/local

CSTD     := -std=c11
WARN     := -Wall -Wextra -Wpedantic -Wshadow -Wstrict-prototypes \
            -Wwrite-strings -Wmissing-prototypes
CPPFLAGS := -Isrc -Iinclude -D_GNU_SOURCE
CFLAGS   := $(CSTD) $(WARN) $(OPT) -g $(CPPFLAGS)
LDFLAGS  :=
LDLIBS   := -lpthread

ifdef STRICT
  CFLAGS += -Werror
endif
ifdef ASAN
  SAN      := -fsanitize=address,undefined -fno-omit-frame-pointer
  CFLAGS  += $(SAN)
  LDFLAGS += $(SAN)
endif

# Feature flags + libraries for the full application build only.
APP_DEFS := -DHAVE_LIBPCAP -DNPA_WITH_NCURSES
APP_LIBS := -lpcap -lncursesw

BUILD   := build
OBJDIR  := $(BUILD)/obj        # application objects (with APP_DEFS)
TOBJDIR := $(BUILD)/tobj       # test objects        (without APP_DEFS)
BINDIR  := $(BUILD)/bin

BIN  := $(BINDIR)/npa
SRCS := $(shell find src -name '*.c')
OBJS := $(patsubst src/%.c,$(OBJDIR)/%.o,$(SRCS))
DEPS := $(OBJS:.o=.d)

# ---- Test wiring: each binary links only the modules it actually needs. ----
RB_OBJS  := $(TOBJDIR)/tests/test_ring_buffer.o \
            $(TOBJDIR)/buffer/ring_buffer.o
DEC_OBJS := $(TOBJDIR)/tests/test_decode.o \
            $(TOBJDIR)/decode/decode.o   $(TOBJDIR)/decode/ethernet.o \
            $(TOBJDIR)/decode/ipv4.o     $(TOBJDIR)/decode/ipv6.o \
            $(TOBJDIR)/decode/tcp.o      $(TOBJDIR)/decode/udp.o \
            $(TOBJDIR)/decode/icmp.o
ANO_OBJS := $(TOBJDIR)/tests/test_anomaly.o \
            $(TOBJDIR)/analysis/anomaly.o $(TOBJDIR)/analysis/patterns.o \
            $(TOBJDIR)/util/log.o
PAT_OBJS := $(TOBJDIR)/tests/test_patterns.o \
            $(TOBJDIR)/analysis/patterns.o $(TOBJDIR)/util/log.o
CFG_OBJS := $(TOBJDIR)/tests/test_config.o \
            $(TOBJDIR)/util/config.o $(TOBJDIR)/util/log.o
STAT_OBJS := $(TOBJDIR)/tests/test_statistics.o \
            $(TOBJDIR)/analysis/statistics.o
TEST_BINS := $(BINDIR)/test_ring_buffer $(BINDIR)/test_decode \
             $(BINDIR)/test_anomaly $(BINDIR)/test_patterns \
             $(BINDIR)/test_config $(BINDIR)/test_statistics
TEST_DEPS := $(RB_OBJS:.o=.d) $(DEC_OBJS:.o=.d) $(ANO_OBJS:.o=.d) \
             $(PAT_OBJS:.o=.d) $(CFG_OBJS:.o=.d) $(STAT_OBJS:.o=.d)

.PHONY: all
all: $(BIN)

$(BIN): $(OBJS)
	@mkdir -p $(BINDIR)
	$(CC) $(LDFLAGS) $^ -o $@ $(APP_LIBS) $(LDLIBS)
	@echo "==> built $@"

# Application objects: compiled WITH feature flags.
$(OBJDIR)/%.o: src/%.c
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) $(APP_DEFS) -MMD -MP -c $< -o $@

# Test objects from src/: compiled WITHOUT feature flags.
$(TOBJDIR)/%.o: src/%.c
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) -MMD -MP -c $< -o $@

# Test objects from tests/: add the tests/ include dir for test_common.h.
$(TOBJDIR)/tests/%.o: tests/%.c
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) -Itests -MMD -MP -c $< -o $@

# ---- Test binaries ----
$(BINDIR)/test_ring_buffer: $(RB_OBJS)
	@mkdir -p $(BINDIR)
	$(CC) $(LDFLAGS) $^ -o $@ $(LDLIBS)
$(BINDIR)/test_decode: $(DEC_OBJS)
	@mkdir -p $(BINDIR)
	$(CC) $(LDFLAGS) $^ -o $@ $(LDLIBS)
$(BINDIR)/test_anomaly: $(ANO_OBJS)
	@mkdir -p $(BINDIR)
	$(CC) $(LDFLAGS) $^ -o $@ $(LDLIBS)
$(BINDIR)/test_patterns: $(PAT_OBJS)
	@mkdir -p $(BINDIR)
	$(CC) $(LDFLAGS) $^ -o $@ $(LDLIBS)
$(BINDIR)/test_config: $(CFG_OBJS)
	@mkdir -p $(BINDIR)
	$(CC) $(LDFLAGS) $^ -o $@ $(LDLIBS)
$(BINDIR)/test_statistics: $(STAT_OBJS)
	@mkdir -p $(BINDIR)
	$(CC) $(LDFLAGS) $^ -o $@ $(LDLIBS)

# ---- Test fixtures: generate a deterministic sample.pcap ----
$(BINDIR)/gen_sample: tests/fixtures/gen_sample.c
	@mkdir -p $(BINDIR)
	$(CC) $(CSTD) $(WARN) $(OPT) -o $@ $<

.PHONY: fixtures
fixtures: $(BINDIR)/gen_sample
	$(BINDIR)/gen_sample tests/fixtures/sample.pcap
	@echo "==> generated tests/fixtures/sample.pcap"

.PHONY: smoke
smoke: $(BIN) fixtures
	@echo "== headless smoke test =="
	./$(BIN) -r tests/fixtures/sample.pcap --headless --json

.PHONY: test
test: $(TEST_BINS)
	@echo "== running unit tests =="
	@fail=0; for t in $(TEST_BINS); do echo; echo ">> $$t"; ./$$t || fail=1; done; \
	 echo; if [ $$fail -eq 0 ]; then echo "ALL TESTS PASSED"; else echo "TESTS FAILED"; fi; \
	 exit $$fail

.PHONY: run
run: $(BIN)
	./$(BIN) $(ARGS)

.PHONY: lint
lint:
	./scripts/lint.sh

.PHONY: format
format:
	./scripts/format.sh

.PHONY: coverage
coverage:
	@echo "== building tests with coverage instrumentation =="
	$(MAKE) clean
	$(MAKE) test CFLAGS="$(CSTD) $(CPPFLAGS) -g -O0 --coverage" LDFLAGS="--coverage"
	@command -v gcovr >/dev/null 2>&1 \
	  && gcovr -r . --exclude tests --print-summary \
	  || echo "gcovr not installed; raw .gcda/.gcno files are under $(BUILD)/"

.PHONY: install
install: $(BIN)
	install -d $(DESTDIR)$(PREFIX)/bin
	install -m 0755 $(BIN) $(DESTDIR)$(PREFIX)/bin/npa
	@echo "==> installed to $(DESTDIR)$(PREFIX)/bin/npa"
	@echo "    grant capture rights: sudo setcap cap_net_raw,cap_net_admin+eip $(DESTDIR)$(PREFIX)/bin/npa"

.PHONY: clean
clean:
	$(RM) -r $(BUILD)
	@echo "==> cleaned"

.PHONY: help
help:
	@sed -n '1,40p' Makefile | sed -n '/^#/p'

-include $(DEPS) $(TEST_DEPS)
