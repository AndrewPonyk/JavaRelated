import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Stat-tile trend sparkline (dataviz stat-tile contract):
 * de-emphasis hue for the line, current point in the accent, no axes/grid,
 * single series (identity carried by the tile label, not color).
 */
@Component({
  selector: 'app-sparkline',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (points().length >= 2) {
      <svg
        [attr.viewBox]="'0 0 ' + W + ' ' + H"
        preserveAspectRatio="none"
        role="img"
        [attr.aria-label]="ariaLabel()"
      >
        <title>{{ ariaLabel() }}</title>
        <polyline
          [attr.points]="path()"
          fill="none"
          stroke="var(--viz-line)"
          stroke-width="2"
          stroke-linejoin="round"
          stroke-linecap="round"
        />
        <circle [attr.cx]="endX()" [attr.cy]="endY()" r="2.5" fill="var(--accent)" />
      </svg>
    }
  `,
  styles: `
    :host { display: block; }
    svg { width: 100%; height: 32px; display: block; }
  `,
})
export class SparklineComponent {
  readonly W = 120;
  readonly H = 32;
  private readonly PAD = 3;

  points = input.required<number[]>();
  label = input<string>('trend');

  private readonly coords = computed(() => {
    const values = this.points();
    if (values.length < 2) {
      return [] as [number, number][];
    }
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min || 1; // flat series → centered line
    const innerH = this.H - 2 * this.PAD;
    const stepX = (this.W - 2 * this.PAD) / (values.length - 1);
    return values.map((v, i): [number, number] => [
      this.PAD + i * stepX,
      this.PAD + (1 - (v - min) / span) * innerH,
    ]);
  });

  readonly path = computed(() =>
    this.coords()
      .map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`)
      .join(' '),
  );

  readonly endX = computed(() => this.coords().at(-1)?.[0] ?? 0);
  readonly endY = computed(() => this.coords().at(-1)?.[1] ?? 0);

  readonly ariaLabel = computed(() => {
    const values = this.points();
    if (values.length < 2) {
      return `${this.label()}: not enough data`;
    }
    const min = Math.min(...values);
    const max = Math.max(...values);
    return (
      `${this.label()}: ${values.length} recent windows, ` +
      `latest ${values.at(-1)?.toFixed(2)}, range ${min.toFixed(2)}–${max.toFixed(2)}`
    );
  });
}
