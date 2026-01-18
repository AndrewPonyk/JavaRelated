import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ClaimService } from '../../services/claim.service';
import { PatientService } from '../../services/patient.service';
import { ProviderService } from '../../services/provider.service';
import { ClaimType, CLAIM_TYPE_LABELS, ClaimInput } from '../../models/claim.model';
import { Patient } from '../../models/patient.model';
import { Provider } from '../../models/provider.model';

@Component({
  selector: 'app-claim-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="claim-form-container">
      <header class="page-header">
        <h1>Submit New Claim</h1>
      </header>

      @if (error()) {
        <div class="alert alert-danger">
          {{ error() }}
        </div>
      }

      @if (success()) {
        <div class="alert alert-success">
          Claim submitted successfully! Claim number: {{ submittedClaimNumber() }}
        </div>
      }

      <form [formGroup]="claimForm" (ngSubmit)="onSubmit()" class="card form-card">
        <div class="form-section">
          <h3>Claim Information</h3>

          <div class="form-row">
            <div class="form-group">
              <label for="type" class="form-label required">Claim Type</label>
              <select
                id="type"
                formControlName="type"
                class="form-input"
                [class.invalid]="isFieldInvalid('type')"
              >
                <option value="">Select Type</option>
                @for (type of claimTypes; track type) {
                  <option [value]="type">{{ getTypeLabel(type) }}</option>
                }
              </select>
              @if (isFieldInvalid('type')) {
                <span class="error-message">Claim type is required</span>
              }
            </div>

            <div class="form-group">
              <label for="amount" class="form-label required">Amount ($)</label>
              <input
                id="amount"
                type="number"
                formControlName="amount"
                class="form-input"
                [class.invalid]="isFieldInvalid('amount')"
                placeholder="0.00"
                min="0.01"
                step="0.01"
              />
              @if (isFieldInvalid('amount')) {
                <span class="error-message">Valid amount is required</span>
              }
            </div>
          </div>

          <div class="form-row">
            <div class="form-group">
              <label for="serviceDate" class="form-label required">Service Date</label>
              <input
                id="serviceDate"
                type="date"
                formControlName="serviceDate"
                class="form-input"
                [class.invalid]="isFieldInvalid('serviceDate')"
                [max]="today"
              />
              @if (isFieldInvalid('serviceDate')) {
                <span class="error-message">Service date is required and cannot be in the future</span>
              }
            </div>

            <div class="form-group">
              <label for="serviceEndDate" class="form-label">Service End Date</label>
              <input
                id="serviceEndDate"
                type="date"
                formControlName="serviceEndDate"
                class="form-input"
                [max]="today"
              />
            </div>
          </div>
        </div>

        <div class="form-section">
          <h3>Patient & Provider</h3>

          <div class="form-row">
            <div class="form-group">
              <label for="patientId" class="form-label required">Patient</label>
              <select
                id="patientId"
                formControlName="patientId"
                class="form-input"
                [class.invalid]="isFieldInvalid('patientId')"
              >
                <option value="">Select Patient</option>
                @for (patient of patients(); track patient.id) {
                  <option [value]="patient.id">
                    {{ patient.fullName }} ({{ patient.memberId }})
                  </option>
                }
              </select>
              @if (isFieldInvalid('patientId')) {
                <span class="error-message">Patient is required</span>
              }
            </div>

            <div class="form-group">
              <label for="providerId" class="form-label required">Provider</label>
              <select
                id="providerId"
                formControlName="providerId"
                class="form-input"
                [class.invalid]="isFieldInvalid('providerId')"
              >
                <option value="">Select Provider</option>
                @for (provider of providers(); track provider.id) {
                  <option [value]="provider.id" [disabled]="!provider.eligibleForClaims">
                    {{ provider.name }} ({{ provider.npi }})
                    @if (!provider.eligibleForClaims) {
                      - Not Eligible
                    }
                  </option>
                }
              </select>
              @if (isFieldInvalid('providerId')) {
                <span class="error-message">Provider is required</span>
              }
            </div>
          </div>
        </div>

        <div class="form-section">
          <h3>Clinical Information</h3>

          <div class="form-row">
            <div class="form-group">
              <label for="diagnosisCodes" class="form-label">Diagnosis Codes (ICD-10)</label>
              <input
                id="diagnosisCodes"
                type="text"
                formControlName="diagnosisCodes"
                class="form-input"
                placeholder="e.g., J06.9, E11.9"
              />
              <span class="hint">Comma-separated ICD-10 codes</span>
            </div>

            <div class="form-group">
              <label for="procedureCodes" class="form-label">Procedure Codes (CPT)</label>
              <input
                id="procedureCodes"
                type="text"
                formControlName="procedureCodes"
                class="form-input"
                placeholder="e.g., 99213, 99214"
              />
              <span class="hint">Comma-separated CPT codes</span>
            </div>
          </div>

          <div class="form-group">
            <label for="notes" class="form-label">Notes</label>
            <textarea
              id="notes"
              formControlName="notes"
              class="form-input form-textarea"
              rows="3"
              placeholder="Additional notes or comments..."
            ></textarea>
          </div>
        </div>

        <div class="form-actions">
          <button
            type="button"
            class="btn btn-secondary"
            (click)="onCancel()"
          >
            Cancel
          </button>
          <button
            type="submit"
            class="btn btn-primary"
            [disabled]="submitting() || !claimForm.valid"
          >
            @if (submitting()) {
              <span class="loading-spinner-sm"></span> Submitting...
            } @else {
              Submit Claim
            }
          </button>
        </div>
      </form>
    </div>
  `,
  styles: [`
    .claim-form-container {
      max-width: 800px;
      margin: 0 auto;
    }

    .page-header h1 {
      margin-bottom: 24px;
    }

    .form-card {
      padding: 32px;
    }

    .form-section {
      margin-bottom: 32px;

      h3 {
        margin-bottom: 16px;
        padding-bottom: 8px;
        border-bottom: 1px solid var(--border-color);
        font-size: 18px;
      }
    }

    .form-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 24px;

      @media (max-width: 600px) {
        grid-template-columns: 1fr;
      }
    }

    .form-group {
      margin-bottom: 16px;
    }

    .form-label {
      display: block;
      margin-bottom: 8px;
      font-weight: 500;
      color: var(--text-primary);

      &.required::after {
        content: ' *';
        color: var(--danger-color);
      }
    }

    .form-input {
      width: 100%;
      padding: 10px 12px;
      border: 1px solid var(--border-color);
      border-radius: 6px;
      font-size: 14px;
      transition: border-color 0.2s, box-shadow 0.2s;

      &:focus {
        outline: none;
        border-color: var(--primary-color);
        box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
      }

      &.invalid {
        border-color: var(--danger-color);
      }

      &:disabled {
        background-color: var(--background-secondary);
        cursor: not-allowed;
      }
    }

    .form-textarea {
      resize: vertical;
      min-height: 80px;
    }

    .error-message {
      display: block;
      margin-top: 4px;
      font-size: 12px;
      color: var(--danger-color);
    }

    .hint {
      display: block;
      margin-top: 4px;
      font-size: 12px;
      color: var(--text-muted);
    }

    .form-actions {
      display: flex;
      justify-content: flex-end;
      gap: 12px;
      padding-top: 24px;
      border-top: 1px solid var(--border-color);
    }

    .alert {
      padding: 16px;
      border-radius: 6px;
      margin-bottom: 24px;
    }

    .alert-danger {
      background: #fef2f2;
      border: 1px solid #fecaca;
      color: #991b1b;
    }

    .alert-success {
      background: #f0fdf4;
      border: 1px solid #bbf7d0;
      color: #166534;
    }

    .loading-spinner-sm {
      display: inline-block;
      width: 14px;
      height: 14px;
      border: 2px solid #ffffff;
      border-top-color: transparent;
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  `]
})
export class ClaimFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly claimService = inject(ClaimService);
  private readonly patientService = inject(PatientService);
  private readonly providerService = inject(ProviderService);

  claimForm!: FormGroup;
  patients = signal<Patient[]>([]);
  providers = signal<Provider[]>([]);
  submitting = signal(false);
  error = signal<string | null>(null);
  success = signal(false);
  submittedClaimNumber = signal<string>('');

  today = new Date().toISOString().split('T')[0];

  claimTypes: ClaimType[] = [
    'MEDICAL', 'DENTAL', 'VISION', 'PHARMACY',
    'MENTAL_HEALTH', 'REHABILITATION', 'LABORATORY',
    'RADIOLOGY', 'EMERGENCY', 'INPATIENT', 'OUTPATIENT'
  ];

  ngOnInit(): void {
    this.initForm();
    this.loadPatients();
    this.loadProviders();
  }

  private initForm(): void {
    this.claimForm = this.fb.group({
      type: ['', Validators.required],
      amount: [null, [Validators.required, Validators.min(0.01), Validators.max(999999999.99)]],
      serviceDate: ['', Validators.required],
      serviceEndDate: [''],
      patientId: ['', Validators.required],
      providerId: ['', Validators.required],
      diagnosisCodes: [''],
      procedureCodes: [''],
      notes: ['']
    });
  }

  private loadPatients(): void {
    this.patientService.getActivePatients().subscribe({
      next: (patients) => this.patients.set(patients),
      error: (err) => console.error('Failed to load patients:', err)
    });
  }

  private loadProviders(): void {
    this.providerService.getActiveProviders().subscribe({
      next: (providers) => this.providers.set(providers),
      error: (err) => console.error('Failed to load providers:', err)
    });
  }

  isFieldInvalid(fieldName: string): boolean {
    const field = this.claimForm.get(fieldName);
    return field ? field.invalid && (field.dirty || field.touched) : false;
  }

  getTypeLabel(type: ClaimType): string {
    return CLAIM_TYPE_LABELS[type] || type;
  }

  onSubmit(): void {
    if (this.claimForm.invalid) {
      this.claimForm.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.error.set(null);
    this.success.set(false);

    const claimInput: ClaimInput = this.claimForm.value;

    this.claimService.submitClaim(claimInput).subscribe({
      next: (claim) => {
        this.submitting.set(false);
        this.success.set(true);
        this.submittedClaimNumber.set(claim.claimNumber);
        this.claimForm.reset();

        // Navigate to the claim detail after a delay
        setTimeout(() => {
          this.router.navigate(['/claims', claim.id]);
        }, 2000);
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(err.message || 'Failed to submit claim');
      }
    });
  }

  onCancel(): void {
    this.router.navigate(['/claims']);
  }
}
