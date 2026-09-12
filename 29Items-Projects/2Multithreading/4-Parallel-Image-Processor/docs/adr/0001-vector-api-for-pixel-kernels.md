# ADR 0001: Vector API for pixel kernels

## Status

Accepted — decision is **not to adopt** `jdk.incubator.vector` in `pip-core`'s production filters
this round.

## Context

`pip-core`'s tile kernels (`GrayscaleFilter`, `BoxBlurFilter`, `SharpenFilter`) operate on packed
ARGB pixels: one `int` per pixel, with each channel extracted via a bit shift and mask, e.g.
`GrayscaleFilter`'s inner loop:

```java
int argb = src[srcRow + x];
int luma = (W_RED * ((argb >> 16) & 0xFF)
        + W_GREEN * ((argb >> 8) & 0xFF)
        + W_BLUE * (argb & 0xFF)) >>> SHIFT;
```

`java.awt.image.BufferedImage`'s `TYPE_INT_ARGB` raster — the format `Pixels` reads/writes
throughout `pip-core` — is packed this way natively; it's not a choice this codebase made, it's
the interop boundary with `java.awt.image` and `ImageIO`.

The question: would rewriting these inner loops against the incubator Vector API
(`jdk.incubator.vector`) meaningfully speed up the per-tile work that `TileProcessingAction`
parallelizes?

## Investigation

`benchmarks/.../VectorApiScalarBenchmark` measures three variants of the same grayscale-luma
arithmetic:

- `scalarPacked` — today's approach, directly on packed ARGB `int[]`.
- `vectorPreSeparated` — the same multiply-add-shift, vectorized, but assuming the channels are
  *already* deinterleaved into separate `int[]` arrays (isolates the arithmetic ceiling).
- `vectorWithDeinterleave` — `vectorPreSeparated` plus the scalar deinterleave step a real
  adoption would need, since bit-shift channel extraction on a packed layout doesn't vectorize.

Run: `java --add-modules jdk.incubator.vector -jar benchmarks/target/benchmarks.jar
VectorApiScalarBenchmark -f 1 -wi 2 -i 3 -jvmArgs "--add-modules jdk.incubator.vector"`.

Measured (1 fork, 1 warmup + 1 measurement iteration — directional, not precise; see
`docs/PERFORMANCE.md` for this project's general JMH methodology caveats):

| pixelCount | scalarPacked | vectorPreSeparated | vectorWithDeinterleave |
|---|---|---|---|
| 4,096 | 6.95 us/op | 1.89 us/op | 11.52 us/op |
| 65,536 | 113.55 us/op | 27.49 us/op | 193.87 us/op |
| 1,048,576 | 2,442.58 us/op | 1,579.52 us/op | 4,397.33 us/op |

`vectorPreSeparated` is 4-6x faster than `scalarPacked` at every size — the arithmetic ceiling is
real. But `vectorWithDeinterleave`, which pays the scalar deinterleave cost a real adoption would
require, is 1.7-2x *slower* than the current scalar approach at every size, not faster — the
deinterleave step doesn't just eat the vector win, it inverts it. This is the concrete evidence
behind the decision below, not just a theoretical concern.

## Decision

Do not adopt the Vector API for `pip-core`'s pixel kernels in this round.

The packed-ARGB layout is the actual obstacle, not the arithmetic. `vectorPreSeparated` shows the
Vector API genuinely speeds up the arithmetic in isolation — but capturing that speedup in
production would mean either:

1. Deinterleaving to per-channel arrays on every tile before the vector loop and re-interleaving
   after (measured by `vectorWithDeinterleave`), which spends a scalar pass undoing exactly the
   layout the vector pass needs — for a single-filter pass like grayscale, this conversion
   overhead eats a large fraction of the arithmetic win; or
2. Restructuring `Tile`/`Pixels`/the whole filter pipeline around a de-interleaved pixel format,
   which is a cross-cutting change to `pip-core`'s data model, not a drop-in loop swap inside one
   filter — it would touch `BoxBlurFilter`, `SharpenFilter`, `GrayscaleFilter`, `Pixels`, and every
   test that asserts on packed-ARGB output (`TileKernelDeterminismTest` et al.).

Neither is justified by this round's scope. A drop-in "swap the inner loop for a vector one" is
not available given the existing pixel format.

## Consequences

- `GrayscaleFilter`/`BoxBlurFilter`/`SharpenFilter` are unchanged.
- `jdk.incubator.vector` is used only in `benchmarks/`, which never appears on `pip-core`'s,
  `pip-app`'s, or `pip-ui`'s compile or runtime classpath — no incubator-module dependency leaks
  into the shipped application.
- Revisit if a future filter is naturally multi-pass and already needs a de-interleaved
  intermediate representation (e.g. a real edge-detection kernel operating per-channel) — at that
  point the deinterleave cost is already paid for other reasons, and `vectorPreSeparated`'s
  numbers become the relevant ones rather than `vectorWithDeinterleave`'s.
- If `jdk.incubator.vector` graduates out of incubator in a future JDK this project adopts, the
  module-availability side of this decision goes away, but the layout-restructuring cost does not
  — re-evaluate specifically against that cost, not against incubator-module friction.
