import { Injectable, signal, computed } from '@angular/core';

/**
 * Authentication service stub.
 * TODO: Integrate with Azure AD B2C for production.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly currentUserSignal = signal<User | null>(null);

  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUserSignal() !== null);
  readonly userRoles = computed(() => this.currentUserSignal()?.roles ?? []);

  /**
   * Simulates login for development.
   * TODO: Replace with actual Azure AD B2C authentication.
   */
  login(email: string, password: string): Promise<User> {
    // Stub implementation
    const user: User = {
      id: 'user-001',
      email,
      name: 'Test User',
      roles: ['claims-viewer', 'claims-processor']
    };

    this.currentUserSignal.set(user);
    return Promise.resolve(user);
  }

  /**
   * Logs out the current user.
   */
  logout(): Promise<void> {
    this.currentUserSignal.set(null);
    return Promise.resolve();
  }

  /**
   * Checks if user has a specific role.
   */
  hasRole(role: string): boolean {
    return this.userRoles().includes(role);
  }

  /**
   * Checks if user can approve claims.
   */
  canApproveClaims(): boolean {
    return this.hasRole('claims-processor') || this.hasRole('admin');
  }

  /**
   * Checks if user can view fraud details.
   */
  canViewFraudDetails(): boolean {
    return this.hasRole('fraud-reviewer') || this.hasRole('admin');
  }

  /**
   * Gets auth token for API requests.
   * TODO: Return actual JWT token from Azure AD.
   */
  getToken(): string | null {
    return this.isAuthenticated() ? 'mock-jwt-token' : null;
  }
}

export interface User {
  id: string;
  email: string;
  name: string;
  roles: string[];
}
