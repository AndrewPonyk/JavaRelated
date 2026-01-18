import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="app-container">
      <nav class="navbar">
        <div class="navbar-brand">
          <span class="brand-icon">+</span>
          Healthcare Claims Processing
        </div>
        <div class="navbar-menu">
          <a routerLink="/dashboard" routerLinkActive="active">Dashboard</a>
          <a routerLink="/claims" routerLinkActive="active">Claims</a>
          <a routerLink="/review-queue" routerLinkActive="active">Review Queue</a>
          <a routerLink="/reports" routerLinkActive="active">Reports</a>
        </div>
        <div class="navbar-user">
          <span class="user-name">Test User</span>
          <button class="btn btn-secondary btn-sm">Logout</button>
        </div>
      </nav>

      <main class="main-content">
        <router-outlet></router-outlet>
      </main>

      <footer class="footer">
        <p>&copy; 2024 Healthcare Claims Processing. All rights reserved.</p>
      </footer>
    </div>
  `,
  styles: [`
    .app-container {
      min-height: 100vh;
      display: flex;
      flex-direction: column;
    }

    .navbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 24px;
      background: white;
      border-bottom: 1px solid var(--border-color);
      box-shadow: var(--shadow-sm);
    }

    .navbar-brand {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 18px;
      font-weight: 600;
      color: var(--primary-color);
    }

    .brand-icon {
      width: 32px;
      height: 32px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--primary-color);
      color: white;
      border-radius: 8px;
      font-size: 20px;
    }

    .navbar-menu {
      display: flex;
      gap: 8px;
    }

    .navbar-menu a {
      padding: 8px 16px;
      color: var(--text-secondary);
      text-decoration: none;
      border-radius: var(--border-radius);
      transition: all 0.2s ease;

      &:hover {
        background: var(--bg-secondary);
        color: var(--text-primary);
      }

      &.active {
        background: var(--primary-color);
        color: white;
      }
    }

    .navbar-user {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .user-name {
      color: var(--text-secondary);
    }

    .btn-sm {
      padding: 6px 12px;
      font-size: 13px;
    }

    .main-content {
      flex: 1;
      padding: 24px;
      max-width: 1400px;
      width: 100%;
      margin: 0 auto;
    }

    .footer {
      padding: 16px 24px;
      text-align: center;
      color: var(--text-muted);
      border-top: 1px solid var(--border-color);
      background: var(--bg-primary);
    }
  `]
})
export class AppComponent {
  title = 'Healthcare Claims Processing';
}
