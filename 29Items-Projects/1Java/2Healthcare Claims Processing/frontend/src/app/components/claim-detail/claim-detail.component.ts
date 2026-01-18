import { Component, OnInit, inject, signal, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ClaimService } from '../../services/claim.service';
import { AuthService } from '../../services/auth.service';
import {
  Claim,
  CLAIM_STATUS_LABELS,
  CLAIM_TYPE_LABELS,
  getStatusBadgeClass
} from '../../models/claim.model';

@Component({
  selector: 'app-claim-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  template: `
    <div class="claim-detail-container">
      <nav class="breadcrumb">
        <a routerLink="/claims">Claims</a>
        <span>/</span>
        <span>{{ claim()?.claimNumber || 'Loading...' }}</span>
      </nav>

      @if (loading()) {
        <div class="loading-container">
          <div class="loading-spinner"></div>
          <p>Loading claim details...</p>
        </div>
      } @else if (error()) {
        <div class="alert alert-danger">
          {{ error() }}
          <a routerLink="/claims" class="btn btn-secondary">Back to Claims</a>
        </div>
      } @else if (claim()) {
        <div class="claim-header">
          <div class="claim-title">
            <h1>{{ claim()!.claimNumber }}</h1>
            <span class="badge" [class]="getStatusBadgeClass(claim()!.status)">
              {{ getStatusLabel(claim()!.status) }}
            </span>
          </div>
          <div class="claim-actions">
            @if (canApprove()) {
              <button class="btn btn-primary" (click)="openApproveModal()">
                Approve
              </button>
              <button class="btn btn-danger" (click)="openDenyModal()">
                Deny
              </button>
            }
          </div>
        </div>

        <div class="claim-grid">
          <div class="card">
            <div class="card-header">Claim Information</div>
            <div class="card-body">
              <dl class="detail-list">
                <dt>Claim Type</dt>
                <dd>{{ getTypeLabel(claim()!.type) }}</dd>

                <dt>Amount</dt>
                <dd>{{ claim()!.amount | currency }}</dd>

                @if (claim()!.allowedAmount) {
                  <dt>Allowed Amount</dt>
                  <dd>{{ claim()!.allowedAmount | currency }}</dd>
                }

                <dt>Service Date</dt>
                <dd>{{ claim()!.serviceDate | date:'mediumDate' }}</dd>

                <dt>Diagnosis Codes</dt>
                <dd>{{ claim()!.diagnosisCodes || '-' }}</dd>

                <dt>Procedure Codes</dt>
                <dd>{{ claim()!.procedureCodes || '-' }}</dd>
              </dl>
            </div>
          </div>

          <div class="card">
            <div class="card-header">Fraud Analysis</div>
            <div class="card-body">
              @if (claim()!.fraudScore !== undefined && claim()!.fraudScore !== null) {
                <div class="fraud-score-display">
                  <div
                    class="score-circle"
                    [class.high]="claim()!.fraudScore! >= 0.7"
                    [class.medium]="claim()!.fraudScore! >= 0.5 && claim()!.fraudScore! < 0.7"
                  >
                    {{ (claim()!.fraudScore! * 100) | number:'1.0-0' }}%
                  </div>
                  <div class="score-label">Fraud Risk Score</div>
                </div>

                @if (claim()!.fraudReasons) {
                  <div class="fraud-reasons">
                    <h4>Risk Factors</h4>
                    <ul>
                      @for (reason of claim()!.fraudReasons!.split(';'); track reason) {
                        <li>{{ reason.trim() }}</li>
                      }
                    </ul>
                  </div>
                }
              } @else {
                <p class="text-muted">No fraud analysis available</p>
              }
            </div>
          </div>

          <div class="card">
            <div class="card-header">Processing History</div>
            <div class="card-body">
              <dl class="detail-list">
                <dt>Submitted By</dt>
                <dd>{{ claim()!.submittedBy || '-' }}</dd>

                <dt>Created At</dt>
                <dd>{{ claim()!.createdAt | date:'medium' }}</dd>

                @if (claim()!.reviewedBy) {
                  <dt>Reviewed By</dt>
                  <dd>{{ claim()!.reviewedBy }}</dd>

                  <dt>Reviewed At</dt>
                  <dd>{{ claim()!.reviewedAt | date:'medium' }}</dd>
                }

                @if (claim()!.denialReason) {
                  <dt>Denial Reason</dt>
                  <dd class="denial-reason">{{ claim()!.denialReason }}</dd>
                }
              </dl>
            </div>
          </div>

          @if (claim()!.notes) {
            <div class="card full-width">
              <div class="card-header">Notes</div>
              <div class="card-body">
                <p>{{ claim()!.notes }}</p>
              </div>
            </div>
          }
        </div>

        <!-- Approve Modal -->
        @if (showApproveModal()) {
          <div class="modal-overlay" (click)="closeModals()">
            <div class="modal" (click)="$event.stopPropagation()">
              <h2>Approve Claim</h2>
              <div class="form-group">
                <label class="form-label">Notes (optional)</label>
                <textarea
                  class="form-input"
                  rows="3"
                  [(ngModel)]="approvalNotes"
                ></textarea>
              </div>
              <div class="modal-actions">
                <button class="btn btn-secondary" (click)="closeModals()">Cancel</button>
                <button class="btn btn-primary" (click)="approveClaim()">
                  Confirm Approval
                </button>
              </div>
            </div>
          </div>
        }

        <!-- Deny Modal -->
        @if (showDenyModal()) {
          <div class="modal-overlay" (click)="closeModals()">
            <div class="modal" (click)="$event.stopPropagation()">
              <h2>Deny Claim</h2>
              <div class="form-group">
                <label class="form-label">Denial Reason *</label>
                <textarea
                  class="form-input"
                  rows="3"
                  [(ngModel)]="denialReason"
                  placeholder="Enter reason for denial..."
                ></textarea>
              </div>
              <div class="modal-actions">
                <button class="btn btn-secondary" (click)="closeModals()">Cancel</button>
                <button
                  class="btn btn-danger"
                  [disabled]="!denialReason"
                  (click)="denyClaim()"
                >
                  Confirm Denial
                </button>
              </div>
            </div>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .claim-detail-container {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .breadcrumb {
      display: flex;
      gap: 8px;
      color: var(--text-secondary);

      a {
        color: var(--primary-color);
        text-decoration: none;

        &:hover {
          text-decoration: underline;
        }
      }
    }

    .claim-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .claim-title {
      display: flex;
      align-items: center;
      gap: 12px;

      h1 {
        margin: 0;
        font-size: 28px;
      }
    }

    .claim-actions {
      display: flex;
      gap: 12px;
    }

    .claim-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
      gap: 20px;
    }

    .full-width {
      grid-column: 1 / -1;
    }

    .detail-list {
      display: grid;
      grid-template-columns: 140px 1fr;
      gap: 12px;

      dt {
        font-weight: 500;
        color: var(--text-secondary);
      }

      dd {
        margin: 0;
      }
    }

    .fraud-score-display {
      text-align: center;
      padding: 20px;
    }

    .score-circle {
      width: 80px;
      height: 80px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      font-weight: 700;
      margin: 0 auto 12px;
      background: #dcfce7;
      color: #166534;

      &.medium {
        background: #fef3c7;
        color: #92400e;
      }

      &.high {
        background: #fee2e2;
        color: #991b1b;
      }
    }

    .score-label {
      color: var(--text-secondary);
    }

    .fraud-reasons {
      margin-top: 20px;
      padding-top: 20px;
      border-top: 1px solid var(--border-color);

      h4 {
        margin: 0 0 12px;
        font-size: 14px;
      }

      ul {
        margin: 0;
        padding-left: 20px;
      }

      li {
        margin-bottom: 4px;
      }
    }

    .denial-reason {
      color: var(--danger-color);
    }

    .modal-overlay {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
    }

    .modal {
      background: white;
      padding: 24px;
      border-radius: var(--border-radius);
      width: 100%;
      max-width: 480px;

      h2 {
        margin: 0 0 20px;
      }
    }

    .modal-actions {
      display: flex;
      justify-content: flex-end;
      gap: 12px;
      margin-top: 20px;
    }

    .loading-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 16px;
      padding: 48px;
    }

    .text-muted {
      color: var(--text-muted);
    }
  `]
})
export class ClaimDetailComponent implements OnInit {
  readonly id = input.required<string>();

  private readonly claimService = inject(ClaimService);
  private readonly authService = inject(AuthService);

  claim = signal<Claim | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);

  showApproveModal = signal(false);
  showDenyModal = signal(false);
  approvalNotes = '';
  denialReason = '';

  ngOnInit(): void {
    this.loadClaim();
  }

  loadClaim(): void {
    this.loading.set(true);
    this.error.set(null);

    this.claimService.getClaimById(this.id()).subscribe({
      next: (claim) => {
        this.claim.set(claim);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err.message);
        this.loading.set(false);
      }
    });
  }

  canApprove(): boolean {
    const claim = this.claim();
    if (!claim) return false;

    const reviewableStatuses = ['PENDING_REVIEW', 'FLAGGED_FRAUD'];
    return reviewableStatuses.includes(claim.status) && this.authService.canApproveClaims();
  }

  openApproveModal(): void {
    this.showApproveModal.set(true);
  }

  openDenyModal(): void {
    this.showDenyModal.set(true);
  }

  closeModals(): void {
    this.showApproveModal.set(false);
    this.showDenyModal.set(false);
    this.approvalNotes = '';
    this.denialReason = '';
  }

  approveClaim(): void {
    const user = this.authService.currentUser();
    if (!user) return;

    this.claimService.approveClaim(
      this.id(),
      user.email,
      this.approvalNotes || undefined
    ).subscribe({
      next: (claim) => {
        this.claim.set(claim);
        this.closeModals();
      },
      error: (err) => {
        alert('Failed to approve claim: ' + err.message);
      }
    });
  }

  denyClaim(): void {
    if (!this.denialReason) return;

    const user = this.authService.currentUser();
    if (!user) return;

    this.claimService.denyClaim(
      this.id(),
      this.denialReason,
      user.email
    ).subscribe({
      next: (claim) => {
        this.claim.set(claim);
        this.closeModals();
      },
      error: (err) => {
        alert('Failed to deny claim: ' + err.message);
      }
    });
  }

  getStatusLabel(status: string): string {
    return CLAIM_STATUS_LABELS[status as keyof typeof CLAIM_STATUS_LABELS] || status;
  }

  getTypeLabel(type: string): string {
    return CLAIM_TYPE_LABELS[type as keyof typeof CLAIM_TYPE_LABELS] || type;
  }

  getStatusBadgeClass(status: string): string {
    return getStatusBadgeClass(status as any);
  }
}
