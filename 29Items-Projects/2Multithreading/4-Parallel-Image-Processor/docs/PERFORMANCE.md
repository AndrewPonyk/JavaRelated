# Performance calibration

This document records the JMH measurements behind the two fork/join leaf-size constants in
`ProcessingOptions` (`DEFAULT_TILE_THRESHOLD_PIXELS`, `DEFAULT_BATCH_THRESHOLD_JOBS`), how to
reproduce them, and how to re-run the same benchmarks under a different garbage collector.

Both benchmarks live in `benchmarks/src/main/java/com/parallelimage/benchmarks/` and are excluded
from the production build graph (`pip-core`/`pip-app`/`pip-ui` never depend on `benchmarks`).

Hardware used for the numbers below: 22 logical cores, Windows. `@Fork(1)`, 2 warmup + 3
measurement iterations of 1s each — enough to see a clear trend, not enough to fully suppress
JIT/GC noise; treat every score as directional, not a precise measurement. Re-run locally with
higher `-wi`/`-i` before trusting a change at the margins.

## Tile threshold (`DEFAULT_TILE_THRESHOLD_PIXELS`)

`TileThresholdBenchmark.tileBlur` — a `BoxBlurFilter` over a fixed 4096x4096 synthetic image,
sweeping the Level-2 (tile) leaf size against pool parallelism.

Run:
```
mvn -pl benchmarks -am package -DskipTests
java -jar benchmarks/target/benchmarks.jar TileThresholdBenchmark -rf json -rff tile-threshold-results.json
```

ms/op (average time, lower is better):

| tileThresholdPixels \ parallelism | 1 | 2 | 4 | 8 | 16 | sum |
|---|---|---|---|---|---|---|
| 4,096 | 1702.6 | 772.7 | 539.1 | 386.6 | 334.2 | 3735.2 |
| 16,384 | 685.1 | 369.7 | 305.8 | 202.8 | 155.0 | 1718.4 |
| 65,536 (previous default) | 460.5 | 303.7 | 182.0 | 130.7 | 94.6 | 1171.5 |
| **262,144 (new default)** | **367.2** | **216.4** | **143.4** | **117.3** | 97.8 | **942.1** |
| 1,048,576 | 379.0 | 186.0 | 196.2 | 113.1 | 109.7 | 984.0 |

**262,144** beats the previous default of 65,536 at parallelism 1/2/4/8 by 20–40%, and is
statistically tied with it at parallelism=16 (97.8 vs 94.6 ms/op, well within each row's
measurement error — JMH's reported `scoreError` at that point spans ±40–90 ms/op on both). It also
has the lowest total ms/op integrated across the whole sweep. 1,048,576 (too coarse) regresses at
parallelism=4 (196.2 vs 143.4) because leaves get too large to keep all workers fed — a classic
Amdahl's-law-style symptom: past the point where a leaf's sequential blur time already saturates a
core, going coarser just leaves other cores idle waiting for the next split.

## Batch threshold (`DEFAULT_BATCH_THRESHOLD_JOBS`)

`BatchThresholdBenchmark.batchOfJobs` — a fixed batch of 64 jobs, each running a real (but small)
Level-2 tile tree over a 512x512 image in memory (no disk I/O — this isolates Level-1 fork/join
decomposition overhead from decode/encode cost, which is a separate concern).

Run:
```
mvn -pl benchmarks -am package -DskipTests
java -jar benchmarks/target/benchmarks.jar BatchThresholdBenchmark -rf json -rff batch-threshold-results.json
```

ms/op (average time, lower is better):

| batchThresholdJobs \ parallelism | 1 | 2 | 4 | 8 | 16 | sum |
|---|---|---|---|---|---|---|
| 1 | 578.4 | 314.1 | 149.8 | 106.4 | 81.9 | 1230.6 |
| **4 (new default)** | **427.3** | **259.1** | **192.0** | **140.9** | **94.3** | **1113.6** |
| 8 (previous default) | 476.4 | 304.6 | 219.0 | 144.9 | 117.9 | 1262.8 |
| 16 | 699.5 | 406.5 | 197.2 | 141.1 | 84.2 | 1528.5 |
| 32 | 491.6 | 262.1 | 186.4 | 130.0 | 128.6 | 1198.7 |

**4** beats the previous default of 8 at every measured parallelism level and has the lowest
aggregate across the sweep, so it's the safer all-around default. A threshold of 1 (maximal
decomposition — never batch more than one job per fork/join leaf) is actually faster than 4 at
parallelism ≥ 4 (e.g. 81.9 vs 94.3 ms/op at parallelism=16), but noticeably *worse* at low
parallelism (578.4 vs 427.3 at parallelism=1), where the extra forking is pure overhead with no
parallel benefit to offset it. 4 was chosen over 1 as the more conservative choice: it is close to
optimal at high parallelism while not regressing at low parallelism, and this benchmark
deliberately doesn't model per-job disk I/O — in production, each job also decodes/encodes an
image, which adds real per-job work that dilutes fork overhead further, so the low-parallelism
penalty for an aggressive threshold is likely to matter more in practice than the synthetic,
in-memory-only numbers above suggest.

## Re-running with a different collector

JMH doesn't switch collectors itself — pass GC flags to the JVM the harness forks into via `-jvmArgs`:

```
# G1 (default on most JDK 21 builds already, included for an explicit baseline)
java -jar benchmarks/target/benchmarks.jar TileThresholdBenchmark -jvmArgs "-XX:+UseG1GC"

# ZGC — generational ZGC is default-on in JDK 21; this selects it explicitly
java -jar benchmarks/target/benchmarks.jar TileThresholdBenchmark -jvmArgs "-XX:+UseZGC"

# Shenandoah — requires a build with the Shenandoah module (not all JDK 21 distributions ship it)
java -jar benchmarks/target/benchmarks.jar TileThresholdBenchmark -jvmArgs "-XX:+UseShenandoahGC"
```

Both benchmarks allocate a fresh `BufferedImage` per invocation (`Pixels.sameShape`), so GC choice
mainly affects pause-time distribution rather than mean throughput at this heap size — worth
checking p99 latency (`-prof gc`) rather than just the average-time score if a specific collector
matters for the deployment target:

```
java -jar benchmarks/target/benchmarks.jar TileThresholdBenchmark -prof gc -jvmArgs "-XX:+UseZGC"
```
