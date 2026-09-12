package com.parallelimage.benchmarks;

import com.parallelimage.nativebridge.NativeImageEnhancer;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Compares per-call overhead of the existing JNI path against a Foreign Function &amp; Memory
 * (FFM) API downcall, plus a plain-Java baseline for calibration.
 *
 * <p><b>Why this doesn't call the same native symbol on both sides.</b> Every function
 * {@code NativeImageEnhancer} exports (see {@code native/include/
 * com_parallelimage_nativebridge_NativeImageEnhancer.h}) takes a {@code JNIEnv*} as its first
 * parameter — that's how the JVM's JNI bridge invokes it. There is no supported public API to
 * synthesize a live {@code JNIEnv*} outside of an actual JNI call context, so an FFM
 * {@code Linker} downcall cannot target those exact exported symbols; doing so would require
 * rewriting the C-side signature to a plain-C-ABI function first, which is itself the real
 * migration cost this benchmark and {@code docs/adr/0002-ffm-api-vs-jni-for-native-enhance.md}
 * are trying to quantify. Instead:
 * <ul>
 *   <li>The JNI side calls {@link NativeImageEnhancer#isAvailable()} — the public API that makes
 *       one real trivial native call ({@code nativeApiVersion()}) when the library is loaded.</li>
 *   <li>The FFM side downcalls {@code strlen} via {@code Linker.nativeLinker().defaultLookup()} —
 *       a plain-C-ABI libc/ucrt symbol available on every platform this runs on, needing no
 *       custom native build.</li>
 * </ul>
 * This is a deliberate substitution, not a same-symbol comparison: it measures each mechanism's
 * baseline dispatch cost, not a literal call-for-call swap.
 *
 * <p><b>Native library availability.</b> {@code pip-native}'s OpenCV library isn't built in every
 * checkout. When it isn't, {@link NativeImageEnhancer#isAvailable()} takes its fast-fail branch
 * (checks {@code NativeLibraryLoader.isLoaded()}, returns {@code false} without reaching the
 * native call) — that is the expected, handled behavior in that environment, not a bug in this
 * benchmark. To measure the real JNI dispatch cost instead of the fallback's cost, run with
 * {@code -Djava.library.path=<pip-native build output dir>} pointing at a built {@code pip_enhance}
 * library.
 *
 * <p>Needs the FFM preview API at both compile and run time (finalized in JDK 22; still preview on
 * JDK 21, which this project targets). Run: {@code java --enable-preview -jar
 * benchmarks/target/benchmarks.jar JniVsFfmCallOverheadBenchmark -f 1 -wi 2 -i 3 -jvmArgs
 * "--enable-preview"}. As with {@link VectorApiScalarBenchmark}, the {@code -jvmArgs} flag is
 * required in addition to the outer flag because JMH forks a fresh JVM per benchmark run.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
public class JniVsFfmCallOverheadBenchmark {

    private Arena arena;
    private MethodHandle strlen;
    private MemorySegment payload;
    private NativeImageEnhancer enhancer;

    @Setup(Level.Trial)
    public void setUp() throws Throwable {
        enhancer = new NativeImageEnhancer();
        arena = Arena.ofShared();
        Linker linker = Linker.nativeLinker();
        MemorySegment strlenAddress = linker.defaultLookup().find("strlen")
                .orElseThrow(() -> new IllegalStateException(
                        "strlen not found in the platform's default native lookup"));
        strlen = linker.downcallHandle(
                strlenAddress, FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
        payload = arena.allocateUtf8String("jni-vs-ffm-call-overhead-benchmark");
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        arena.close();
    }

    /** Floor for the other two: a direct call the JIT is free to inline away entirely. */
    @Benchmark
    public void javaBaseline(Blackhole blackhole) {
        blackhole.consume(trivialJavaCall());
    }

    /** See the class javadoc for why this calls {@code isAvailable()}, not {@code enhance()}. */
    @Benchmark
    public void jniCall(Blackhole blackhole) {
        blackhole.consume(enhancer.isAvailable());
    }

    /** See the class javadoc for why this downcalls {@code strlen}, not a project-owned symbol. */
    @Benchmark
    public void ffmCall(Blackhole blackhole) throws Throwable {
        long length = (long) strlen.invokeExact(payload);
        blackhole.consume(length);
    }

    private static boolean trivialJavaCall() {
        return Thread.currentThread().isVirtual();
    }
}
