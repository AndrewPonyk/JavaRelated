# Makefile — single source of build truth for perflib.
# Quick start:  make            (build lib + bench + example, debug)
#               make test       (build & run the test suite)
#               make bench BUILD=release && make run-bench
#               make package    (versioned tarball in dist/)
# Override:     CC, AS, BUILD={debug|release}, ARCH_FLAGS, SANITIZE={0|1}, PREFIX

# ---- configuration ----------------------------------------------------------
CC         ?= cc
# GNU Make predefines AS=as (the GNU assembler). Force nasm, but still honor an
# explicit override from the environment or command line (origin != "default").
ifeq ($(origin AS),default)
AS         := nasm
endif
AR         ?= ar
BUILD      ?= debug
PREFIX     ?= /usr/local

VERSION    := 0.1.0
SONAME_MAJ := 0

# ---- OS detection: NASM object format + shared-lib conventions --------------
UNAME_S := $(shell uname -s 2>/dev/null || echo Windows)
ifeq ($(UNAME_S),Darwin)
  ASFORMAT := macho64
  SHLIB    := libperflib.$(VERSION).dylib
  SHLINK   := libperflib.dylib
  SOFLAGS  := -dynamiclib -install_name $(PREFIX)/lib/libperflib.dylib
else ifneq (,$(findstring MINGW,$(UNAME_S))$(findstring MSYS,$(UNAME_S))$(findstring Windows,$(UNAME_S)))
  ASFORMAT := win64
  SHLIB    := perflib.dll
  SHLINK   := perflib.dll
  SOFLAGS  := -shared
else
  ASFORMAT := elf64
  SHLIB    := libperflib.so.$(VERSION)
  SHLINK   := libperflib.so
  SOFLAGS  := -shared -Wl,-soname,libperflib.so.$(SONAME_MAJ)
endif

# ---- flags ------------------------------------------------------------------
ARCH_FLAGS ?= -mavx2 -mfma
WARN       := -Wall -Wextra
# gnu11 = C11 plus POSIX/GNU visibility (clock_gettime, mmap, __builtin_*),
# which this project needs for timers, the page-safety test, and CPUID builtins.
CSTD       := -std=gnu11
INCLUDES   := -Iinclude

ifeq ($(BUILD),release)
  OPT   := -O3 -DNDEBUG
  ASDBG :=
else
  OPT   := -O0 -g -DPERFLIB_DEBUG
  ASDBG := -g -F dwarf
endif

ifeq ($(SANITIZE),1)
  SAN := -fsanitize=address,undefined -fno-omit-frame-pointer
else
  SAN :=
endif

CFLAGS  := $(CSTD) $(WARN) $(OPT) $(ARCH_FLAGS) $(SAN) $(INCLUDES) -fPIC $(CFLAGS_EXTRA)
ASFLAGS := -f $(ASFORMAT) $(ASDBG)
LDFLAGS := $(SAN)
LDLIBS  := -lm

# ---- sources / objects ------------------------------------------------------
BUILDDIR := build/$(BUILD)
C_SRC    := $(wildcard src/c/*.c) $(wildcard src/dispatch/*.c)
ASM_SRC  := $(wildcard src/asm/*.asm)
C_OBJ    := $(patsubst %.c,$(BUILDDIR)/%.o,$(C_SRC))
ASM_OBJ  := $(patsubst %.asm,$(BUILDDIR)/%.o,$(ASM_SRC))
OBJ      := $(C_OBJ) $(ASM_OBJ)

STATIC      := $(BUILDDIR)/libperflib.a
SHARED      := $(BUILDDIR)/$(SHLIB)
PCFILE      := $(BUILDDIR)/perflib.pc
TEST_SRC    := $(wildcard tests/*.c)
BENCH_SRC   := $(wildcard bench/*.c)
TEST_BIN    := $(BUILDDIR)/perflib_tests
BENCH_BIN   := $(BUILDDIR)/perflib_bench
EXAMPLE_BIN := $(BUILDDIR)/example

.PHONY: all lib static shared test bench example run-bench \
        format lint package install uninstall clean distclean help

all: lib bench example
lib: static shared pkgconfig
static: $(STATIC)
shared: $(SHARED)
pkgconfig: $(PCFILE)

# generate perflib.pc from the template (substitute prefix + version)
$(PCFILE): perflib.pc.in
	@mkdir -p $(dir $@)
	sed -e 's|@PREFIX@|$(PREFIX)|g' -e 's|@VERSION@|$(VERSION)|g' $< > $@

# ---- compile rules ----------------------------------------------------------
$(BUILDDIR)/%.o: %.c
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) -c $< -o $@

$(BUILDDIR)/%.o: %.asm
	@mkdir -p $(dir $@)
	$(AS) $(ASFLAGS) $< -o $@

# ---- libraries --------------------------------------------------------------
$(STATIC): $(OBJ)
	@mkdir -p $(dir $@)
	$(AR) rcs $@ $(OBJ)

$(SHARED): $(OBJ)
	@mkdir -p $(dir $@)
	$(CC) $(SOFLAGS) $(LDFLAGS) -o $@ $(OBJ) $(LDLIBS)
ifneq ($(ASFORMAT),win64)
	ln -sf $(SHLIB) $(BUILDDIR)/$(SHLINK)
endif

# ---- executables (link the static lib for simplicity) -----------------------
$(TEST_BIN): $(TEST_SRC) $(STATIC)
	$(CC) $(CFLAGS) $(TEST_SRC) $(STATIC) $(LDFLAGS) $(LDLIBS) -o $@

$(BENCH_BIN): $(BENCH_SRC) $(STATIC)
	$(CC) $(CFLAGS) -Ibench $(BENCH_SRC) $(STATIC) $(LDFLAGS) $(LDLIBS) -o $@

$(EXAMPLE_BIN): examples/example_usage.c $(STATIC)
	$(CC) $(CFLAGS) examples/example_usage.c $(STATIC) $(LDFLAGS) $(LDLIBS) -o $@

test: $(TEST_BIN)
	./$(TEST_BIN)

bench: $(BENCH_BIN)
example: $(EXAMPLE_BIN)

run-bench: $(BENCH_BIN)
	./$(BENCH_BIN) --suite all

# ---- quality ----------------------------------------------------------------
format:
	@command -v clang-format >/dev/null 2>&1 && \
	  clang-format -i $$(find src include tests bench examples -name '*.c' -o -name '*.h') \
	  || echo "clang-format not found (skipping)"

lint:
	@command -v cppcheck >/dev/null 2>&1 && \
	  cppcheck --enable=warning,portability --std=c11 -Iinclude src bench tests \
	  || echo "cppcheck not found (skipping)"

# ---- packaging / install ----------------------------------------------------
package: lib
	@mkdir -p dist/perflib-$(VERSION)/lib/pkgconfig dist/perflib-$(VERSION)/include
	cp -r include/perflib dist/perflib-$(VERSION)/include/
	cp $(STATIC) $(SHARED) dist/perflib-$(VERSION)/lib/
	cp $(PCFILE) dist/perflib-$(VERSION)/lib/pkgconfig/
	cp LICENSE README.md dist/perflib-$(VERSION)/
	cd dist && tar czf perflib-$(VERSION).tar.gz perflib-$(VERSION)
	cd dist && (sha256sum perflib-$(VERSION).tar.gz > perflib-$(VERSION).tar.gz.sha256 || \
	            shasum -a 256 perflib-$(VERSION).tar.gz > perflib-$(VERSION).tar.gz.sha256)
	@echo "packaged dist/perflib-$(VERSION).tar.gz"

install: lib
	install -d $(DESTDIR)$(PREFIX)/lib/pkgconfig $(DESTDIR)$(PREFIX)/include/perflib
	install -m644 include/perflib/*.h $(DESTDIR)$(PREFIX)/include/perflib/
	install -m644 $(STATIC) $(DESTDIR)$(PREFIX)/lib/
	install -m755 $(SHARED) $(DESTDIR)$(PREFIX)/lib/
	install -m644 $(PCFILE) $(DESTDIR)$(PREFIX)/lib/pkgconfig/

clean:
	rm -rf build

distclean: clean
	rm -rf dist charts results.csv *.json

help:
	@echo "targets: all lib static shared test bench example run-bench package install clean"
	@echo "vars:    BUILD=debug|release  CC=$(CC)  AS=$(AS)  ARCH_FLAGS='$(ARCH_FLAGS)'  SANITIZE=0|1"
	@echo "host:    UNAME_S=$(UNAME_S)  ASFORMAT=$(ASFORMAT)"
