import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, retry, timer } from 'rxjs';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';

import { environment } from '../../../environments/environment';
import { CurrentMetric, MetricHistoryPoint } from '../models/metric.model';

@Injectable({ providedIn: 'root' })
export class MetricsService {
  private readonly http = inject(HttpClient);
  private socket$?: WebSocketSubject<CurrentMetric>;

  /** REST snapshot of the hot store — the dashboard's initial paint. */
  getSnapshot(): Observable<CurrentMetric[]> {
    return this.http.get<CurrentMetric[]>(`${environment.apiBaseUrl}/metrics/current`);
  }

  /**
   * History: `live` = rolling 500 ms windows (Redis hot store),
   * `daily` = warehouse marts / warmed daily cache.
   */
  getHistory(
    metric: string,
    options: { limit?: number; granularity?: 'live' | 'daily' } = {},
  ): Observable<MetricHistoryPoint[]> {
    return this.http.get<MetricHistoryPoint[]>(
      `${environment.apiBaseUrl}/metrics/${metric}/history`,
      { params: { limit: options.limit ?? 120, granularity: options.granularity ?? 'live' } },
    );
  }

  /**
   * Live updates pushed by the server (< 1 s end-to-end). One shared socket;
   * exponential-ish backoff reconnect. The dashboard surfaces disconnects —
   * frozen numbers must never masquerade as live ones.
   */
  liveMetrics(): Observable<CurrentMetric> {
    if (!this.socket$) {
      const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
      const params = new URLSearchParams();
      const token = localStorage.getItem('etl_token');
      if (token) {
        params.set('token', token); // browsers cannot set WS headers
      } else if (environment.apiKey) {
        params.set('api_key', environment.apiKey);
      }
      const query = params.size ? `?${params.toString()}` : '';
      this.socket$ = webSocket<CurrentMetric>({
        url: `${protocol}://${location.host}${environment.metricsStreamPath}${query}`,
      });
    }
    return this.socket$.pipe(
      retry({ delay: (_err, retryCount) => timer(Math.min(1000 * 2 ** retryCount, 15_000)) }),
    );
  }
}
