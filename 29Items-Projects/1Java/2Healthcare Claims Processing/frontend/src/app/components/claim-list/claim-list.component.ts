import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ClaimService } from '../../services/claim.service';
import {
  Claim,
  ClaimStatus,
  CLAIM_STATUS_LABELS,
  CLAIM_TYPE_LABELS,
  getStatusBadgeClass
} from '../../models/claim.model';

@Component({
  selector: 'app-claim-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  template: `
    <div class="claim-list-container">
      <header class="page-header">
        <h1>{{ isReviewQueue() ? 'Review Queue' : 'Claims' }}</h1>
        <div class="header-actions">
          <input
            type="text"
            class="form-input search-input"
            placeholder="Search claims..."
            [(ngModel)]="searchQuery"
            (input)="onSearch()"
          />
          <select
            class="form-input status-filter"
            [(ngModel)]="selectedStatus"
            (change)="onFilterChange()"
          >
            <option value="">All Statuses</option>
            @for (status of statusOptions; track status) {
              <option [value]="status">{{ getStatusLabel(status) }}</option>
            }
          </select>
        </div>
      </header>

      @if (loading()) {
        <div class="loading-container">
          <div class="loading-spinner"></div>
          <p>Loading claims...</p>
        </div>
      } @else if (error()) {
        <div class="alert alert-danger">
          {{ error() }}
        </div>
      } @else {
        <div class="card">
          <table class="table">
            <thead>
              <tr>
                <th>Claim #</th>
                <th>Type</th>
                <th>Status</th>
                <th>Amount</th>
                <th>Service Date</th>
                <th>Fraud Score</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (claim of filteredClaims(); track claim.id) {
                <tr>
                  <td>
                    <a [routerLink]="['/claims', claim.id]" class="claim-link">
                      {{ claim.claimNumber }}
                    </a>
                  </td>
                  <td>{{ getTypeLabel(claim.type) }}</td>
                  <td>
                    <span class="badge" [class]="getStatusBadgeClass(claim.status)">
                      {{ getStatusLabel(claim.status) }}
                    </span>
                  </td>
                  <td>{{ claim.amount | currency }}</td>
                  <td>{{ claim.serviceDate | date:'mediumDate' }}</td>
                  <td>
                    @if (claim.fraudScore !== undefined && claim.fraudScore !== null) {
                      <span
                        class="fraud-score"
                        [class.high]="claim.fraudScore >= 0.7"
                        [class.medium]="claim.fraudScore >= 0.5 && claim.fraudScore < 0.7"
                      >
                        {{ (claim.fraudScore * 100) | number:'1.0-0' }}%
                      </span>
                    } @else {
                      <span class="text-muted">-</span>
                    }
                  </td>
                  <td>{{ claim.createdAt | date:'short' }}</td>
                  <td>
                    <a [routerLink]="['/claims', claim.id]" class="btn btn-secondary btn-sm">
                      View
                    </a>
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="8" class="empty-state">
                    No claims found
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        <div class="pagination">
          <button
            class="btn btn-secondary"
            [disabled]="currentPage() === 0"
            (click)="previousPage()"
          >
            Previous
          </button>
          <span class="page-info">Page {{ currentPage() + 1 }}</span>
          <button
            class="btn btn-secondary"
            [disabled]="!hasNextPage()"
            (click)="nextPage()"
          >
            Next
          </button>
        </div>
      }
    </div>
  `,
  styles: [`
    .claim-list-container {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .page-header h1 {
      margin: 0;
      font-size: 24px;
    }

    .header-actions {
      display: flex;
      gap: 12px;
    }

    .search-input {
      width: 250px;
    }

    .status-filter {
      width: 180px;
    }

    .loading-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 16px;
      padding: 48px;
    }

    .claim-link {
      color: var(--primary-color);
      text-decoration: none;
      font-weight: 500;

      &:hover {
        text-decoration: underline;
      }
    }

    .fraud-score {
      padding: 4px 8px;
      border-radius: 4px;
      font-weight: 500;

      &.high {
        background: #fee2e2;
        color: #991b1b;
      }

      &.medium {
        background: #fef3c7;
        color: #92400e;
      }
    }

    .text-muted {
      color: var(--text-muted);
    }

    .empty-state {
      text-align: center;
      padding: 48px;
      color: var(--text-muted);
    }

    .pagination {
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 16px;
    }

    .page-info {
      color: var(--text-secondary);
    }
  `]
})
export class ClaimListComponent implements OnInit {
  private readonly claimService = inject(ClaimService);
  private readonly route = inject(ActivatedRoute);

  claims = signal<Claim[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  currentPage = signal(0);
  hasNextPage = signal(true);

  searchQuery = '';
  selectedStatus = '';

  isReviewQueue = signal(false);

  statusOptions: ClaimStatus[] = [
    'SUBMITTED', 'PENDING_ADJUDICATION', 'PENDING_REVIEW',
    'FLAGGED_FRAUD', 'APPROVED', 'DENIED', 'PAID'
  ];

  filteredClaims = computed(() => {
    let result = this.claims();

    if (this.searchQuery) {
      const query = this.searchQuery.toLowerCase();
      result = result.filter(c =>
        c.claimNumber.toLowerCase().includes(query)
      );
    }

    return result;
  });

  ngOnInit(): void {
    this.route.data.subscribe(data => {
      this.isReviewQueue.set(data['reviewQueue'] === true);
      this.loadClaims();
    });
  }

  loadClaims(): void {
    this.loading.set(true);
    this.error.set(null);

    const loadFn = this.isReviewQueue()
      ? this.claimService.getClaimsRequiringReview()
      : this.claimService.getClaims(
          this.selectedStatus ? { status: this.selectedStatus as ClaimStatus } : undefined,
          { page: this.currentPage(), size: 20 }
        );

    loadFn.subscribe({
      next: (claims) => {
        this.claims.set(claims);
        this.loading.set(false);
        this.hasNextPage.set(claims.length === 20);
      },
      error: (err) => {
        this.error.set(err.message);
        this.loading.set(false);
      }
    });
  }

  onSearch(): void {
    // Filter is computed, no need to reload
  }

  onFilterChange(): void {
    this.currentPage.set(0);
    this.loadClaims();
  }

  previousPage(): void {
    if (this.currentPage() > 0) {
      this.currentPage.update(p => p - 1);
      this.loadClaims();
    }
  }

  nextPage(): void {
    this.currentPage.update(p => p + 1);
    this.loadClaims();
  }

  getStatusLabel(status: ClaimStatus): string {
    return CLAIM_STATUS_LABELS[status] || status;
  }

  getTypeLabel(type: string): string {
    return CLAIM_TYPE_LABELS[type as keyof typeof CLAIM_TYPE_LABELS] || type;
  }

  getStatusBadgeClass(status: ClaimStatus): string {
    return getStatusBadgeClass(status);
  }
}
