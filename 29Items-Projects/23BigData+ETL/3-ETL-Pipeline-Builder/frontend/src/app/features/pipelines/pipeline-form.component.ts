import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { Pipeline } from '../../core/models/pipeline.model';
import { PipelinesService } from '../../core/services/pipelines.service';

// Mirrors the API's Pydantic constraints (api/app/schemas/pipeline.py).
const NAME_PATTERN = /^[a-z][a-z0-9_-]{2,63}$/;
const CRON_PATTERN = /^\S+ \S+ \S+ \S+ \S+$/;

@Component({
  selector: 'app-pipeline-form',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="panel" [formGroup]="form" (ngSubmit)="submit()">
      <h3>Register a pipeline</h3>
      <div class="row">
        <div>
          <label for="name">Name</label>
          <input id="name" formControlName="name" placeholder="orders-daily" />
          @if (touchedInvalid('name')) {
            <div class="field-error">lowercase, digits, - or _; 3–64 chars; starts with a letter</div>
          }
        </div>
        <div>
          <label for="schedule">Schedule (cron)</label>
          <input id="schedule" formControlName="schedule" placeholder="0 2 * * *" />
          @if (touchedInvalid('schedule')) {
            <div class="field-error">five space-separated cron fields</div>
          }
        </div>
      </div>
      <div class="row">
        <div>
          <label for="source">Source</label>
          <input id="source" formControlName="source" placeholder="s3://lake/raw/orders/" />
          @if (touchedInvalid('source')) {
            <div class="field-error">required</div>
          }
        </div>
        <div>
          <label for="target">Target</label>
          <input id="target" formControlName="target" placeholder="analytics.marts.fct_…" />
          @if (touchedInvalid('target')) {
            <div class="field-error">required</div>
          }
        </div>
      </div>
      <div class="row">
        <div>
          <label for="transform_ref">Transform ref <span class="muted">(optional)</span></label>
          <input id="transform_ref" formControlName="transform_ref" placeholder="dbt selector or Glue job" />
        </div>
        <div>
          <label for="description">Description <span class="muted">(optional)</span></label>
          <input id="description" formControlName="description" />
        </div>
      </div>
      @if (serverError()) {
        <div class="field-error">{{ serverError() }}</div>
      }
      <div class="actions">
        <button type="submit" [disabled]="form.invalid || busy()">
          {{ busy() ? 'Creating…' : 'Create pipeline' }}
        </button>
      </div>
    </form>
  `,
  styles: `
    form { display: flex; flex-direction: column; gap: 0.75rem; margin-bottom: 1rem; }
    h3 { margin: 0 0 0.25rem; }
    .row { display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; }
    .actions { display: flex; justify-content: flex-end; }
    @media (max-width: 700px) { .row { grid-template-columns: 1fr; } }
  `,
})
export class PipelineFormComponent {
  private readonly pipelinesService = inject(PipelinesService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);

  created = output<Pipeline>();

  readonly busy = signal(false);
  readonly serverError = signal('');

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.pattern(NAME_PATTERN)]],
    schedule: ['', [Validators.required, Validators.pattern(CRON_PATTERN)]],
    source: ['', Validators.required],
    target: ['', Validators.required],
    transform_ref: [''],
    description: [''],
  });

  touchedInvalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.touched && c.invalid;
  }

  submit(): void {
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.serverError.set('');
    this.pipelinesService
      .create(this.form.getRawValue())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (pipeline) => {
          this.busy.set(false);
          this.form.reset();
          this.created.emit(pipeline);
        },
        error: (err: HttpErrorResponse) => {
          this.busy.set(false);
          this.serverError.set(
            err.status === 409
              ? 'A pipeline with this name already exists.'
              : `Could not create pipeline (${err.status || 'network error'}).`,
          );
        },
      });
  }
}
