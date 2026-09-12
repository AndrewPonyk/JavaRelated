import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Pipeline, PipelineCreate } from '../models/pipeline.model';

@Injectable({ providedIn: 'root' })
export class PipelinesService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/pipelines`;

  list(): Observable<Pipeline[]> {
    return this.http.get<Pipeline[]>(this.base);
  }

  create(payload: PipelineCreate): Observable<Pipeline> {
    return this.http.post<Pipeline>(this.base, payload);
  }

  update(id: string, changes: Partial<PipelineCreate & { status: string }>): Observable<Pipeline> {
    return this.http.patch<Pipeline>(`${this.base}/${id}`, changes);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
