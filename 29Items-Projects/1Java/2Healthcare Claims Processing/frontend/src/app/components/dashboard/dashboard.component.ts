import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ClaimService } from '../../services/claim.service';
import { Claim, ClaimStatus, CLAIM_STATUS_LABELS } from '../../models/claim.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="dashboard-container">
      <h1>Dashboard</h1>

      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-value">{{ stats().totalClaims }}</div>
          <div class="stat-label">Total Claims</div>
        </div>
        <div class="stat-card pending">
          <div class="stat-value">{{ stats().pendingReview }}</div>
          <div class="stat-label">Pending Review</div>
        </div>
        <div class="stat-card fraud">
          <div class="stat-value">{{ stats().fraudFlagged }}</div>
          <div class="stat-label">Fraud Flagged</div>
        </div>
        <div class="stat-card approved">
          <div class="stat-value">{{ stats().approved }}</div>
          <div class="stat-label">Approved Today</div>
        </div>
      </div>

      <div class="dashboard-grid">
        <div class="card">
          <div class="card-header">
            Claims Requiring Review
            <a routerLink="/review-queue" class="view-all">View All</a>
          </div>
          <div class="card-body">
            @if (loading()) {
              <div class="loading-spinner"></div>
            } @else {
              <table class="table">
                <thead>
                  <tr>
                    <th>Claim #</th>
                    <th>Status</th>
                    <th>Amount</th>
                    <th>Fraud Score</th>
                  </tr>
                </thead>
                <tbody>
                  @for (claim of reviewClaims(); track claim.id) {
                    <tr>
                      <td>
                        <a [routerLink]="['/claims', claim.id]">{{ claim.claimNumber }}</a>
                      </td>
                      <td>
                        <span class="badge" [class]="getStatusBadgeClass(claim.status)">
                          {{ getStatusLabel(claim.status) }}
                        </span>
                      </td>
                      <td>{{ claim.amount | currency }}</td>
                      <td>
                        @if (claim.fraudScore !== undefined && claim.fraudScore !== null) {
                          {{ (claim.fraudScore * 100) | number:'1.0-0' }}%
                        } @else {
                          -
                        }
                      </td>
                    </tr>
                  } @empty {
                    <tr>
                      <td colspan="4" class="text-center">No claims requiring review</td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          </div>
        </div>

        <div class="card">
          <div class="card-header">Recent Activity</div>
          <div class="card-body">
            <ul class="activity-list">
              @for (activity of recentActivity(); track activity.id) {
                <li class="activity-item">
                  <div class="activity-icon" [class]="activity.type">
                    {{ activity.icon }}
                  </div>
                  <div class="activity-content">
                    <div class="activity-message">{{ activity.message }}</div>
                    <div class="activity-time">{{ activity.timestamp | date:'short' }}</div>
                  </div>
                </li>
              } @empty {
                <li class="empty-state">No recent activity</li>
              }
            </ul>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .dashboard-container {
      display: flex;
      flex-direction: column;
      gap: 24px;

      h1 {
        margin: 0;
      }
    }

    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
    }

    .stat-card {
      background: white;
      padding: 20px;
      border-radius: var(--border-radius);
      border: 1px solid var(--border-color);
      text-align: center;

      .stat-value {
        font-size: 36px;
        font-weight: 700;
        color: var(--text-primary);
      }

      .stat-label {
        color: var(--text-secondary);
        margin-top: 4px;
      }

      &.pending .stat-value {
        color: var(--warning-color);
      }

      &.fraud .stat-value {
        color: var(--danger-color);
      }

      &.approved .stat-value {
        color: var(--success-color);
      }
    }

    .dashboard-grid {
      display: grid;
      grid-template-columns: 2fr 1fr;
      gap: 20px;
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .view-all {
      font-size: 14px;
      color: var(--primary-color);
      text-decoration: none;

      &:hover {
        text-decoration: underline;
      }
    }

    .activity-list {
      list-style: none;
      padding: 0;
      margin: 0;
    }

    .activity-item {
      display: flex;
      gap: 12px;
      padding: 12px 0;
      border-bottom: 1px solid var(--border-color);

      &:last-child {
        border-bottom: none;
      }
    }

    .activity-icon {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--bg-secondary);
      font-size: 14px;

      &.approved {
        background: #dcfce7;
      }

      &.denied {
        background: #fee2e2;
      }

      &.fraud {
        background: #fef3c7;
      }
    }

    .activity-content {
      flex: 1;
    }

    .activity-message {
      font-size: 14px;
    }

    .activity-time {
      font-size: 12px;
      color: var(--text-muted);
      margin-top: 2px;
    }

    .empty-state {
      text-align: center;
      padding: 20px;
      color: var(--text-muted);
    }

    .text-center {
      text-align: center;
    }

    @media (max-width: 1024px) {
      .dashboard-grid {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class DashboardComponent implements OnInit {
  private readonly claimService = inject(ClaimService);

  loading = signal(true);
  reviewClaims = signal<Claim[]>([]);

  stats = signal({
    totalClaims: 0,
    pendingReview: 0,
    fraudFlagged: 0,
    approved: 0
  });

  recentActivity = signal<Activity[]>([
    { id: '1', type: 'approved', icon: '\u2713', message: 'Claim CLM-001 approved', timestamp: new Date() },
    { id: '2', type: 'fraud', icon: '!', message: 'Fraud alert for CLM-002', timestamp: new Date() },
    { id: '3', type: 'denied', icon: '\u2717', message: 'Claim CLM-003 denied', timestamp: new Date() },
  ]);

  ngOnInit(): void {
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    this.loading.set(true);

    this.claimService.getClaimsRequiringReview().subscribe({
      next: (claims) => {
        this.reviewClaims.set(claims.slice(0, 5));
        this.stats.update(s => ({
          ...s,
          pendingReview: claims.filter(c => c.status === 'PENDING_REVIEW').length,
          fraudFlagged: claims.filter(c => c.status === 'FLAGGED_FRAUD').length
        }));
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });

    // TODO: Load actual stats from API
    this.stats.update(s => ({
      ...s,
      totalClaims: 1234,
      approved: 45
    }));
  }

  getStatusLabel(status: ClaimStatus): string {
    return CLAIM_STATUS_LABELS[status] || status;
  }

  getStatusBadgeClass(status: ClaimStatus): string {
    switch (status) {
      case 'PENDING_REVIEW': return 'badge-pending';
      case 'FLAGGED_FRAUD': return 'badge-fraud';
      default: return 'badge-submitted';
    }
  }
}

interface Activity {
  id: string;
  type: 'approved' | 'denied' | 'fraud' | 'submitted';
  icon: string;
  message: string;
  timestamp: Date;
}
