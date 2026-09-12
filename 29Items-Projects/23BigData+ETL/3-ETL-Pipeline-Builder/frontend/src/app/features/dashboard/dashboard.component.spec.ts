import { TestBed } from '@angular/core/testing';
import { EMPTY, of } from 'rxjs';

import { CurrentMetric } from '../../core/models/metric.model';
import { MetricsService } from '../../core/services/metrics.service';
import { DashboardComponent, formatMetricValue } from './dashboard.component';

describe('formatMetricValue', () => {
  it('compacts large values and keeps precision on small ones', () => {
    expect(formatMetricValue(12_900)).toBe('12.9K');
    expect(formatMetricValue(123.4)).toBe('123');
    expect(formatMetricValue(31.719)).toBe('31.72');
  });

  it('renders ratios as percentages', () => {
    expect(formatMetricValue(0.0667, true)).toBe('6.7');
  });
});

describe('DashboardComponent', () => {
  function setup(snapshot: CurrentMetric[]) {
    const metricsStub: Partial<MetricsService> = {
      getSnapshot: () => of(snapshot),
      liveMetrics: () => EMPTY,
      getHistory: () => of([]),
    };
    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [{ provide: MetricsService, useValue: metricsStub }],
    });
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('renders a tile per metric from the snapshot', () => {
    const fixture = setup([
      {
        metric: 'orders_per_second',
        value: 28,
        window_start_ms: Date.now() - 500,
        window_ms: 500,
      },
    ]);

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Orders per second');
    expect(text).toContain('28.00');
  });

  it('shows the empty hint when no metrics exist yet', () => {
    const fixture = setup([]);

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('No metrics yet');
  });

  it('marks tiles stale when the window is old', () => {
    const fixture = setup([
      { metric: 'orders_per_second', value: 28, window_start_ms: Date.now() - 60_000, window_ms: 500 },
    ]);

    const tile = (fixture.nativeElement as HTMLElement).querySelector('.tile');
    expect(tile?.classList.contains('stale')).toBeTrue();
  });
});
