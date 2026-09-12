import { DatePipe, DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { switchMap, timer } from 'rxjs';

import { AnomalyAlert } from '../../core/models/alert.model';
import { AlertsService } from '../../core/services/alerts.service';

const POLL_MS = 15_000;

@Component({
  selector: 'app-alert-feed',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>Anomaly alerts</h2>
    <p class="muted">
      ML detector output on streaming metrics (EWMA z-score baseline). Refreshes every 15 s.
    </p>

    @if (alerts().length === 0) {
      <div class="panel muted">
        No anomalies detected. Inject one locally:
        <code>python scripts/seed_kafka_events.py --burst</code>
      </div>
    } @else {
      @for (alert of alerts(); track alert.alert_id) {
        <div class="panel alert" [attr.data-severity]="alert.severity" [class.acked]="alert.acknowledged">
          <div class="head">
            <strong>{{ alert.metric }}</strong>
            <span class="chip" [attr.data-severity]="alert.severity">{{ alert.severity }}</span>
            @if (alert.acknowledged) {
              <span class="chip acked-chip">acknowledged</span>
            } @else {
              <button class="ghost" (click)="acknowledge(alert)">Acknowledge</button>
            }
          </div>
          <span>{{ alert.message }}</span>
          <span class="muted">
            z = {{ alert.score | number: '1.1-1' }} · {{ alert.triggered_at | date: 'medium' }}
          </span>
        </div>
      }
    }
  `,
  styles: `
    .alert { display: flex; flex-direction: column; gap: 0.3rem; margin-bottom: 0.75rem; }
    .alert[data-severity='critical'] { border-color: var(--crit); }
    .alert[data-severity='warning'] { border-color: var(--warn); }
    .alert.acked { opacity: 0.6; }
    .head { display: flex; align-items: center; gap: 0.6rem; }
    .head button { margin-left: auto; padding: 0.2rem 0.6rem; font-size: 0.8rem; }
    .chip {
      font-size: 0.75rem; padding: 0.1rem 0.5rem; border-radius: 999px;
      border: 1px solid var(--border); color: var(--muted);
    }
    .chip[data-severity='critical'] { color: var(--crit); border-color: var(--crit); }
    .chip[data-severity='warning'] { color: var(--warn); border-color: var(--warn); }
  `,
})
export class AlertFeedComponent implements OnInit {
  private readonly alertsService = inject(AlertsService);
  private readonly destroyRef = inject(DestroyRef);

  readonly alerts = signal<AnomalyAlert[]>([]);

  ngOnInit(): void {
    timer(0, POLL_MS)
      .pipe(
        switchMap(() => this.alertsService.recent()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (items) => this.alerts.set(items),
        error: () => undefined, // keep the last known feed on transient failures
      });
  }

  acknowledge(alert: AnomalyAlert): void {
    this.alertsService
      .acknowledge(alert.alert_id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () =>
          this.alerts.update((items) =>
            items.map((a) => (a.alert_id === alert.alert_id ? { ...a, acknowledged: true } : a)),
          ),
        error: () => undefined,
      });
  }
}
