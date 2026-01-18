import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface FraudAlertData {
  claimId: string;
  claimNumber: string;
  fraudScore: number;
  fraudReasons?: string;
  status: string;
}

@Component({
  selector: 'app-fraud-alert',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (alert && alert.fraudScore >= threshold) {
      <div
        class="fraud-alert"
        [class.high]="alert.fraudScore >= 0.7"
        [class.medium]="alert.fraudScore >= 0.5 && alert.fraudScore < 0.7"
        [class.low]="alert.fraudScore < 0.5"
      >
        <div class="alert-header">
          <div class="alert-icon">
            @if (alert.fraudScore >= 0.7) {
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                <line x1="12" y1="9" x2="12" y2="13"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            } @else {
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="8" x2="12" y2="12"/>
                <line x1="12" y1="16" x2="12.01" y2="16"/>
              </svg>
            }
          </div>
          <div class="alert-title">
            @if (alert.fraudScore >= 0.7) {
              High Fraud Risk Detected
            } @else if (alert.fraudScore >= 0.5) {
              Moderate Fraud Risk
            } @else {
              Low Fraud Risk Indicator
            }
          </div>
          @if (dismissible) {
            <button class="dismiss-btn" (click)="onDismiss()">×</button>
          }
        </div>

        <div class="alert-body">
          <div class="score-section">
            <div class="score-label">Risk Score</div>
            <div class="score-value">
              <div class="score-bar">
                <div
                  class="score-fill"
                  [style.width.%]="alert.fraudScore * 100"
                ></div>
              </div>
              <span class="score-number">{{ (alert.fraudScore * 100) | number:'1.0-0' }}%</span>
            </div>
          </div>

          @if (alert.fraudReasons) {
            <div class="reasons-section">
              <div class="reasons-label">Risk Factors</div>
              <ul class="reasons-list">
                @for (reason of getReasons(); track reason) {
                  <li>{{ reason }}</li>
                }
              </ul>
            </div>
          }

          @if (showActions) {
            <div class="alert-actions">
              <button class="btn btn-secondary btn-sm" (click)="onReview()">
                Review Claim
              </button>
              @if (alert.fraudScore >= 0.7) {
                <button class="btn btn-danger btn-sm" (click)="onFlag()">
                  Flag for Investigation
                </button>
              }
            </div>
          }
        </div>
      </div>
    }
  `,
  styles: [`
    .fraud-alert {
      border-radius: 8px;
      overflow: hidden;
      margin-bottom: 16px;

      &.high {
        background: #fef2f2;
        border: 1px solid #fecaca;

        .alert-header {
          background: #fee2e2;
          color: #991b1b;
        }

        .score-fill {
          background: #dc2626;
        }
      }

      &.medium {
        background: #fffbeb;
        border: 1px solid #fde68a;

        .alert-header {
          background: #fef3c7;
          color: #92400e;
        }

        .score-fill {
          background: #d97706;
        }
      }

      &.low {
        background: #f0f9ff;
        border: 1px solid #bae6fd;

        .alert-header {
          background: #e0f2fe;
          color: #075985;
        }

        .score-fill {
          background: #0284c7;
        }
      }
    }

    .alert-header {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
    }

    .alert-icon {
      display: flex;
      align-items: center;
    }

    .alert-title {
      flex: 1;
      font-weight: 600;
      font-size: 14px;
    }

    .dismiss-btn {
      background: none;
      border: none;
      font-size: 20px;
      cursor: pointer;
      opacity: 0.7;
      transition: opacity 0.2s;

      &:hover {
        opacity: 1;
      }
    }

    .alert-body {
      padding: 16px;
    }

    .score-section {
      margin-bottom: 16px;
    }

    .score-label,
    .reasons-label {
      font-size: 12px;
      font-weight: 500;
      text-transform: uppercase;
      color: var(--text-secondary);
      margin-bottom: 8px;
    }

    .score-value {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .score-bar {
      flex: 1;
      height: 8px;
      background: rgba(0, 0, 0, 0.1);
      border-radius: 4px;
      overflow: hidden;
    }

    .score-fill {
      height: 100%;
      border-radius: 4px;
      transition: width 0.5s ease-out;
    }

    .score-number {
      font-weight: 700;
      font-size: 18px;
    }

    .reasons-list {
      margin: 0;
      padding-left: 20px;

      li {
        margin-bottom: 4px;
        font-size: 14px;
        color: var(--text-primary);
      }
    }

    .alert-actions {
      display: flex;
      gap: 8px;
      margin-top: 16px;
      padding-top: 16px;
      border-top: 1px solid rgba(0, 0, 0, 0.1);
    }

    .btn-sm {
      padding: 6px 12px;
      font-size: 13px;
    }
  `]
})
export class FraudAlertComponent {
  @Input() alert: FraudAlertData | null = null;
  @Input() threshold = 0.3;
  @Input() dismissible = true;
  @Input() showActions = true;

  @Output() review = new EventEmitter<string>();
  @Output() flag = new EventEmitter<string>();
  @Output() dismiss = new EventEmitter<void>();

  getReasons(): string[] {
    if (!this.alert?.fraudReasons) return [];
    return this.alert.fraudReasons.split(';').map(r => r.trim()).filter(r => r);
  }

  onReview(): void {
    if (this.alert) {
      this.review.emit(this.alert.claimId);
    }
  }

  onFlag(): void {
    if (this.alert) {
      this.flag.emit(this.alert.claimId);
    }
  }

  onDismiss(): void {
    this.dismiss.emit();
  }
}
