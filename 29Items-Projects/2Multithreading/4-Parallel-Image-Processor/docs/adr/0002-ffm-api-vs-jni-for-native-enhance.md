# ADR 0002: FFM API vs. JNI for `NativeImageEnhancer`

## Status

Accepted — decision is **not to migrate** `NativeImageEnhancer` off JNI this round.

## Context

`pip-native`'s `NativeImageEnhancer` calls into an OpenCV-backed C++ library (`pip_enhance`)
through hand-written JNI: a generated header
(`native/include/com_parallelimage_nativebridge_NativeImageEnhancer.h`), `private static native`
method declarations, and direct `ByteBuffer` marshalling for pixel data. Its javadoc already
documents *why* it marshals through direct `ByteBuffer`s rather than heap arrays: stable-address,
GC-pause-free access to pixel data across the JNI boundary.

The Foreign Function & Memory (FFM) API (`java.lang.foreign` — preview on JDK 21, finalized in
JDK 22) offers `Linker`/`MethodHandle` downcalls and `Arena`/`MemorySegment` as a modern
alternative to JNI. The question: should `NativeImageEnhancer` migrate to it?

## Investigation

Two things became clear while designing `benchmarks/.../JniVsFfmCallOverheadBenchmark`:

1. **FFM cannot be a drop-in call-path replacement for the existing JNI symbols.** Every function
   `pip_enhance` exports takes `JNIEnv*` as its first parameter — that's how the JVM's JNI bridge
   invokes it. There's no supported public API to synthesize a live `JNIEnv*` outside of an
   actual JNI call context, so an FFM `Linker` downcall cannot target
   `Java_com_parallelimage_nativebridge_NativeImageEnhancer_nativeEnhance` (or any of its
   siblings) as-is. Migrating would mean rewriting the C++ side to a plain-C-ABI signature
   (dropping `JNIEnv*`/`jclass`/`jobject` in favor of raw pointers and primitive sizes) — a
   surface change to `pip_enhance.cpp`'s public entry points, not just a Java-side swap.
2. **FFM's safety property is one JNI already has.** `NativeImageEnhancer`'s existing
   direct-`ByteBuffer` marshalling already gives stable-address, GC-pause-free access — the same
   property `MemorySegment`/`Arena` provide. So the realistic case for migrating isn't "JNI is
   unsafe and FFM is safe" — it's a tradeoff between call-overhead/ergonomics and rewrite cost.

Because same-symbol comparison isn't possible, the benchmark measures each mechanism's baseline
dispatch cost via a substitute call on each side (documented in full in the benchmark's own
javadoc):

- JNI: `NativeImageEnhancer.isAvailable()` — the public API that makes one real trivial native
  call when the library is loaded.
- FFM: a downcall to `strlen` via `Linker.nativeLinker().defaultLookup()` — a universally
  available libc/ucrt symbol needing no custom native build.
- A plain-Java baseline call, for calibration against JIT-inlined-away overhead.

Run: `java --enable-preview -jar benchmarks/target/benchmarks.jar
JniVsFfmCallOverheadBenchmark -f 1 -wi 2 -i 3 -jvmArgs "--enable-preview"`.

Note: in a checkout where `pip-native`'s OpenCV library hasn't been built, `isAvailable()` takes
its fast-fail branch and measures fallback cost, not real JNI dispatch — pass
`-Djava.library.path=<pip-native build output dir>` to measure the real thing.

## Decision

Do not migrate `NativeImageEnhancer` to the FFM API this round.

- The safety argument for migrating doesn't hold — `NativeImageEnhancer` already avoids the
  unsafe patterns (raw pointers into movable heap memory) that FFM is often pitched as fixing.
- The migration cost is real and cross-cutting: it requires changing `pip_enhance.cpp`'s exported
  signatures (dropping the JNI calling convention), regenerating/removing the JNI header, and
  rewriting `NativeImageEnhancer`'s Java-side marshalling around `MemorySegment`/`Arena` instead
  of `ByteBuffer` — all for a call-overhead difference that, per-call, is negligible next to the
  cost of one `enhance()` invocation's actual OpenCV work (CLAHE/denoise/super-resolution on a
  full image).
- FFM is still a preview API on JDK 21 (this project's target release), which would mean shipping
  `--enable-preview` in `pip-native`'s production runtime — an availability/compatibility cost on
  top of the rewrite cost, for a benefit this workload doesn't need.

## Consequences

- `NativeImageEnhancer.java` and `pip_enhance.cpp`/its JNI header are unchanged.
- `java.lang.foreign` is used only in `benchmarks/`, compiled and run with `--enable-preview`
  scoped to that module — it never appears on `pip-native`'s or the shipped application's
  compile/runtime path.
- Revisit if a future native integration is greenfield (no existing JNI boundary to migrate away
  from) — at that point FFM's ergonomics (no header generation, no `javah`/`javac -h` step, plain
  Java method handles) are a real advantage with none of this ADR's rewrite cost, and the decision
  calculus is different.
