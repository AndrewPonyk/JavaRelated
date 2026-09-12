import { clamp01, easeInOutCubic, interpolateTransform } from '@/core/math/interpolation';
import { IDENTITY, type Mat2 } from '@/core/math/matrix2';

/**
 * Tweens one 2×2 matrix into another over a fixed duration with easing,
 * using rotation-aware (polar-decomposition) interpolation so rotations
 * animate as rotations. Pure bookkeeping — no Three.js, no timers; the
 * render loop feeds it dt.
 */
export class TransformAnimator {
  private from: Mat2 = IDENTITY;
  private to: Mat2 = IDENTITY;
  private elapsed = 0;
  private duration = 0;
  private running = false;

  current: Mat2 = IDENTITY;

  get isRunning(): boolean {
    return this.running;
  }

  begin(from: Mat2, to: Mat2, durationSeconds: number): void {
    this.from = from;
    this.to = to;
    this.duration = Math.max(0.01, durationSeconds);
    this.elapsed = 0;
    this.running = true;
  }

  /** Jump without animating (initial mount, prefers-reduced-motion). */
  snapTo(matrix: Mat2): void {
    this.current = matrix;
    this.running = false;
  }

  update(dtSeconds: number): Mat2 {
    if (!this.running) return this.current;
    this.elapsed += dtSeconds;
    const t = clamp01(this.elapsed / this.duration);
    this.current = interpolateTransform(this.from, this.to, easeInOutCubic(t));
    if (t >= 1) this.running = false;
    return this.current;
  }
}
