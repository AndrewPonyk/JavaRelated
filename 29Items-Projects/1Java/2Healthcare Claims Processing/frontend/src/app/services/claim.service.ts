import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { environment } from '@env/environment';
import { Claim, ClaimInput, ClaimStatus, ClaimConnection, ClaimFilter, Pagination } from '../models/claim.model';

/**
 * Service for claim-related REST API operations.
 */
@Injectable({
  providedIn: 'root'
})
export class ClaimService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/claims`;

  /**
   * Submits a new claim.
   */
  submitClaim(claim: ClaimInput): Observable<Claim> {
    return this.http.post<Claim>(this.baseUrl, claim).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Gets a claim by ID.
   */
  getClaimById(id: string): Observable<Claim> {
    return this.http.get<Claim>(`${this.baseUrl}/${id}`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Gets a claim by claim number.
   */
  getClaimByNumber(claimNumber: string): Observable<Claim> {
    return this.http.get<Claim>(`${this.baseUrl}/number/${claimNumber}`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Gets paginated list of claims with optional filters.
   */
  getClaims(filter?: ClaimFilter, pagination?: Pagination): Observable<Claim[]> {
    let params = new HttpParams();

    if (filter?.status) {
      params = params.set('status', filter.status);
    }
    if (pagination) {
      params = params.set('page', pagination.page.toString());
      params = params.set('size', pagination.size.toString());
    }

    return this.http.get<Claim[]>(this.baseUrl, { params }).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Gets claims for a specific patient.
   */
  getClaimsForPatient(patientId: string): Observable<Claim[]> {
    return this.http.get<Claim[]>(`${this.baseUrl}/patient/${patientId}`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Gets claims requiring review.
   */
  getClaimsRequiringReview(): Observable<Claim[]> {
    return this.http.get<Claim[]>(`${this.baseUrl}/review-queue`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Updates claim status.
   */
  updateClaimStatus(id: string, status: ClaimStatus, notes?: string): Observable<Claim> {
    let params = new HttpParams().set('status', status);
    if (notes) {
      params = params.set('notes', notes);
    }

    return this.http.patch<Claim>(`${this.baseUrl}/${id}/status`, null, { params }).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Approves a claim.
   */
  approveClaim(id: string, reviewedBy: string, notes?: string): Observable<Claim> {
    let params = new HttpParams().set('reviewedBy', reviewedBy);
    if (notes) {
      params = params.set('notes', notes);
    }

    return this.http.post<Claim>(`${this.baseUrl}/${id}/approve`, null, { params }).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Denies a claim.
   */
  denyClaim(id: string, reason: string, reviewedBy: string): Observable<Claim> {
    const params = new HttpParams()
      .set('reason', reason)
      .set('reviewedBy', reviewedBy);

    return this.http.post<Claim>(`${this.baseUrl}/${id}/deny`, null, { params }).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Processes a claim through adjudication.
   */
  processClaim(id: string): Observable<Claim> {
    return this.http.post<Claim>(`${this.baseUrl}/${id}/process`, null).pipe(
      catchError(this.handleError)
    );
  }

  private handleError(error: any): Observable<never> {
    console.error('ClaimService error:', error);
    return throwError(() => new Error(error.error?.message || 'An error occurred'));
  }
}
