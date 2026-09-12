import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { Pipeline, PipelineStatus } from '../../core/models/pipeline.model';
import { PipelinesService } from '../../core/services/pipelines.service';
import { PipelineFormComponent } from './pipeline-form.component';

@Component({
  selector: 'app-pipeline-list',
  standalone: true,
  imports: [DatePipe, PipelineFormComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2>Pipelines</h2>

    <app-pipeline-form (created)="load()" />

    @if (error()) {
      <div class="panel">Failed to load pipelines. <button (click)="load()">Retry</button></div>
    } @else if (pipelines().length === 0) {
      <div class="panel muted">No pipelines registered yet — create the first one above.</div>
    } @else {
      <table class="panel">
        <thead>
          <tr>
            <th>Name</th><th>Schedule</th><th>Source → Target</th><th>Status</th><th>Updated</th><th></th>
          </tr>
        </thead>
        <tbody>
          @for (p of pipelines(); track p.id) {
            <tr>
              <td>{{ p.name }}</td>
              <td><code>{{ p.schedule }}</code></td>
              <td class="muted route">{{ p.source }} → {{ p.target }}</td>
              <td><span class="status" [attr.data-status]="p.status">{{ p.status }}</span></td>
              <td class="muted">{{ p.updated_at | date: 'short' }}</td>
              <td class="row-actions">
                <button class="ghost" (click)="toggle(p)">
                  {{ p.status === 'active' ? 'Pause' : 'Activate' }}
                </button>
                <button class="ghost danger" (click)="remove(p)">Delete</button>
              </td>
            </tr>
          }
        </tbody>
      </table>
    }
  `,
  styles: `
    table { width: 100%; border-collapse: collapse; }
    th, td { text-align: left; padding: 0.5rem 0.75rem; }
    thead th { color: var(--muted); font-weight: 500; }
    .route { max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .status[data-status='active'] { color: var(--ok); }
    .status[data-status='paused'] { color: var(--warn); }
    .row-actions { display: flex; gap: 0.4rem; justify-content: flex-end; }
    .danger { color: var(--crit); border-color: var(--crit); }
  `,
})
export class PipelineListComponent implements OnInit {
  private readonly pipelinesService = inject(PipelinesService);
  private readonly destroyRef = inject(DestroyRef);

  readonly pipelines = signal<Pipeline[]>([]);
  readonly error = signal(false);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.error.set(false);
    this.pipelinesService
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (items) => this.pipelines.set(items),
        error: () => this.error.set(true),
      });
  }

  toggle(pipeline: Pipeline): void {
    const status: PipelineStatus = pipeline.status === 'active' ? 'paused' : 'active';
    this.pipelinesService
      .update(pipeline.id, { status })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: () => this.load(), error: () => this.error.set(true) });
  }

  remove(pipeline: Pipeline): void {
    if (!confirm(`Delete pipeline "${pipeline.name}"?`)) {
      return;
    }
    this.pipelinesService
      .delete(pipeline.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: () => this.load(), error: () => this.error.set(true) });
  }
}
