import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';

import { SparklineComponent } from '../../core/components/sparkline.component';
import { CurrentMetric } from '../../core/models/metric.model';
import { MetricsService } from '../../core/services/metrics.service';

type LoadState = 'loading' | 'ready' | 'error';

interface TileVm extends CurrentMetric {
  label: string;
  unit: string;
  display: string;
  trend: number[];
  stale: boolean;
}

const METRIC_META: Record<string, { label: string; unit: string; ratio?: boolean }> = {
  orders_per_second: { label: 'Orders per second', unit: '/s' },
  revenue_per_second: { label: 'Revenue per second', unit: 'EUR/s' },
  avg_order_value: { label: 'Avg order value', unit: 'EUR' },
  checkout_error_rate: { label: 'Checkout error rate', unit: '%', ratio: true },
};

const TREND_POINTS = 48;
const STALE_AFTER_MS = 5_000;

/** Stat-tile value formatting: auto-compact, proportional figures. */
export function formatMetricValue(value: number, ratio = false): string {
  if (ratio) {
    return (value * 100).toFixed(1);
  }
  if (Math.abs(value) >= 10_000) {
    return new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 1 }).format(
      value,
    );
  }
  return Math.abs(value) >= 100 ? value.toFixed(0) : value.toFixed(2);
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [SparklineComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>Live business metrics</h2>
    <p class="muted">500 ms windows pushed over WebSocket — reconnects automatically.</p>

    @switch (state()) {
      @case ('loading') {
        <div class="panel muted">Loading snapshot…</div>
      }
      @case ('error') {
        <div class="panel error">
          Could not reach the metrics API.
          <button (click)="reload()">Retry</button>
        </div>
      }
      @case ('ready') {
        @if (tiles().length === 0) {
          <div class="panel muted">
            No metrics yet — start the processor and seed events
            (<code>python scripts/seed_kafka_events.py</code>).
          </div>
        } @else {
          <div class="grid">
            @for (tile of tiles(); track tile.metric) {
              <div class="panel tile" [class.stale]="tile.stale">
                <span class="label muted">{{ tile.label }}</span>
                <span class="value"
                  >{{ tile.display }} <span class="unit muted">{{ tile.unit }}</span></span
                >
                <app-sparkline [points]="tile.trend" [label]="tile.label" />
                <span class="window muted">
                  {{ tile.stale ? 'stale — no fresh windows' : 'window ' + tile.window_ms + ' ms' }}
                </span>
              </div>
            }
          </div>
          @if (!live()) {
            <p class="muted reconnect">⟳ live stream disconnected — retrying…</p>
          }
        }
      }
    }
  `,
  styles: `
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
      gap: 1rem;
      margin-top: 1rem;
    }
    .tile { display: flex; flex-direction: column; gap: 0.4rem; }
    .tile.stale { opacity: 0.55; }
    .value { font-size: 2rem; font-weight: 600; }
    .unit { font-size: 0.9rem; font-weight: 400; }
    .window { font-size: 0.8rem; }
    .error { border-color: var(--crit); }
    .reconnect { margin-top: 0.75rem; }
    button { margin-left: 0.75rem; }
  `,
})
export class DashboardComponent implements OnInit {
  private readonly metricsService = inject(MetricsService);
  private readonly destroyRef = inject(DestroyRef);

  readonly state = signal<LoadState>('loading');
  readonly live = signal(false);
  readonly tiles = signal<TileVm[]>([]);

  private readonly byMetric = new Map<string, CurrentMetric>();
  private readonly trends = new Map<string, number[]>();

  ngOnInit(): void {
    this.reload();

    this.metricsService
      .liveMetrics()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (update) => {
          this.live.set(true);
          this.byMetric.set(update.metric, update);
          this.pushTrend(update.metric, update.value);
          this.publish();
        },
        error: () => this.live.set(false),
      });

    // Staleness must update even when the stream goes quiet.
    interval(2_000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.publish());
  }

  reload(): void {
    this.state.set('loading');
    this.metricsService
      .getSnapshot()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (snapshot) => {
          snapshot.forEach((metric) => {
            this.byMetric.set(metric.metric, metric);
            this.seedTrend(metric.metric);
          });
          this.publish();
          this.state.set('ready');
        },
        error: () => this.state.set('error'),
      });
  }

  private seedTrend(metric: string): void {
    this.metricsService
      .getHistory(metric, { limit: TREND_POINTS, granularity: 'live' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (points) => {
          const seeded = points.map((p) => p.value);
          const existing = this.trends.get(metric) ?? [];
          this.trends.set(metric, [...seeded, ...existing].slice(-TREND_POINTS));
          this.publish();
        },
        error: () => undefined, // trend is progressive enhancement, never fatal
      });
  }

  private pushTrend(metric: string, value: number): void {
    const trend = this.trends.get(metric) ?? [];
    trend.push(value);
    this.trends.set(metric, trend.slice(-TREND_POINTS));
  }

  private publish(): void {
    const now = Date.now();
    const tiles = [...this.byMetric.values()]
      .map((metric): TileVm => {
        const meta = METRIC_META[metric.metric] ?? { label: metric.metric, unit: '' };
        return {
          ...metric,
          label: meta.label,
          unit: meta.unit,
          display: formatMetricValue(metric.value, meta.ratio),
          trend: [...(this.trends.get(metric.metric) ?? [])],
          stale: now - (metric.window_start_ms + metric.window_ms) > STALE_AFTER_MS,
        };
      })
      .sort((a, b) => a.label.localeCompare(b.label));
    this.tiles.set(tiles);
  }
}
