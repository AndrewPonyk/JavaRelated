import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { environment } from '@env/environment';
import { Patient, PatientInput } from '../models/patient.model';

/**
 * Service for patient-related REST API operations.
 */
@Injectable({
  providedIn: 'root'
})
export class PatientService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/patients`;

  /**
   * Creates a new patient.
   */
  createPatient(patient: PatientInput): Observable<Patient> {
    return this.http.post<Patient>(this.baseUrl, patient).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Gets a patient by ID.
   */
  getPatientById(id: string): Observable<Patient> {
    return this.http.get<Patient>(`${this.baseUrl}/${id}`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Gets a patient by member ID.
   */
  getPatientByMemberId(memberId: string): Observable<Patient> {
    return this.http.get<Patient>(`${this.baseUrl}/member/${memberId}`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Gets paginated list of patients.
   */
  getPatients(page = 0, size = 20): Observable<Patient[]> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<Patient[]>(this.baseUrl, { params }).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Gets active patients only.
   */
  getActivePatients(): Observable<Patient[]> {
    return this.http.get<Patient[]>(`${this.baseUrl}/active`).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Searches patients by name.
   */
  searchPatients(name: string): Observable<Patient[]> {
    const params = new HttpParams().set('name', name);
    return this.http.get<Patient[]>(`${this.baseUrl}/search`, { params }).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Updates a patient.
   */
  updatePatient(id: string, patient: PatientInput): Observable<Patient> {
    return this.http.put<Patient>(`${this.baseUrl}/${id}`, patient).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Deactivates a patient.
   */
  deactivatePatient(id: string): Observable<Patient> {
    return this.http.post<Patient>(`${this.baseUrl}/${id}/deactivate`, null).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Reactivates a patient.
   */
  reactivatePatient(id: string): Observable<Patient> {
    return this.http.post<Patient>(`${this.baseUrl}/${id}/reactivate`, null).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Deletes a patient.
   */
  deletePatient(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`).pipe(
      catchError(this.handleError)
    );
  }

  private handleError(error: any): Observable<never> {
    console.error('PatientService error:', error);
    return throwError(() => new Error(error.error?.message || 'An error occurred'));
  }
}
