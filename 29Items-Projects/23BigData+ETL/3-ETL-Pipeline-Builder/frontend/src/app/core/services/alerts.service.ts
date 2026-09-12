import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AnomalyAlert } from '../models/alert.model';

@Injectable({ providedIn: 'root' })
export class AlertsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/alerts`;

  recent(limit = 50): Observable<AnomalyAlert[]> {
    return this.http.get<AnomalyAlert[]>(this.base, { params: { limit } });
  }

  acknowledge(alertId: string): Observable<{ alert_id: string; acknowledged: boolean }> {
    return this.http.post<{ alert_id: string; acknowledged: boolean }>(
      `${this.base}/${alertId}/ack`,
      {},
    );
  }
}
