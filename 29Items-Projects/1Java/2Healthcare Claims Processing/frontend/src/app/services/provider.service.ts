import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { environment } from '@env/environment';
import { Provider, ProviderInput } from '../models/provider.model';

/**
 * Service for provider-related REST API operations.
 */
@Injectable({
  providedIn: 'root'
})
export class ProviderService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/providers`;

  /**
   * Creates a new provider.
   */
  createProvider(provider: ProviderInput): Observable<Provider> {
    return this.http.post<Provider>(this.baseUrl, provider).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Gets a provider by ID.
   */
  getProviderById(id: string): Observable<Provider> {
    return this.http.get<Provider>(`${this.baseUrl}/${id}`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Gets a provider by NPI.
   */
  getProviderByNpi(npi: string): Observable<Provider> {
    return this.http.get<Provider>(`${this.baseUrl}/npi/${npi}`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Gets paginated list of providers.
   */
  getProviders(page = 0, size = 20): Observable<Provider[]> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<Provider[]>(this.baseUrl, { params }).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Gets active providers only.
   */
  getActiveProviders(): Observable<Provider[]> {
    return this.http.get<Provider[]>(`${this.baseUrl}/active`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Gets in-network providers.
   */
  getInNetworkProviders(): Observable<Provider[]> {
    return this.http.get<Provider[]>(`${this.baseUrl}/in-network`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Gets providers by specialty.
   */
  getProvidersBySpecialty(specialty: string): Observable<Provider[]> {
    return this.http.get<Provider[]>(`${this.baseUrl}/specialty/${specialty}`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Gets fraud-flagged providers.
   */
  getFraudFlaggedProviders(): Observable<Provider[]> {
    return this.http.get<Provider[]>(`${this.baseUrl}/fraud-flagged`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Searches providers by name.
   */
  searchProviders(name: string): Observable<Provider[]> {
    const params = new HttpParams().set('name', name);
    return this.http.get<Provider[]>(`${this.baseUrl}/search`, { params }).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Updates a provider.
   */
  updateProvider(id: string, provider: ProviderInput): Observable<Provider> {
    return this.http.put<Provider>(`${this.baseUrl}/${id}`, provider).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Updates fraud risk score.
   */
  updateFraudScore(id: string, score: number): Observable<Provider> {
    const params = new HttpParams().set('score', score.toString());
    return this.http.patch<Provider>(`${this.baseUrl}/${id}/fraud-score`, null, { params }).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Deactivates a provider.
   */
  deactivateProvider(id: string): Observable<Provider> {
    return this.http.post<Provider>(`${this.baseUrl}/${id}/deactivate`, null).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Reactivates a provider.
   */
  reactivateProvider(id: string): Observable<Provider> {
    return this.http.post<Provider>(`${this.baseUrl}/${id}/reactivate`, null).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Deletes a provider.
   */
  deleteProvider(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`).pipe(
      catchError(this.handleError)
    );
  }

  private handleError(error: any): Observable<never> {
    console.error('ProviderService error:', error);
    return throwError(() => new Error(error.error?.message || 'An error occurred'));
  }
}
