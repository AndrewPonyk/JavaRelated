import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="topbar">
      <span class="brand">⚡ ETL Pipeline Builder</span>
      <nav>
        <a routerLink="/" [routerLinkActiveOptions]="{ exact: true }" routerLinkActive="active">
          Live metrics
        </a>
        <a routerLink="/pipelines" routerLinkActive="active">Pipelines</a>
        <a routerLink="/alerts" routerLinkActive="active">Alerts</a>
      </nav>
    </header>
    <main>
      <router-outlet />
    </main>
  `,
  styles: `
    .topbar {
      display: flex;
      align-items: center;
      gap: 2rem;
      padding: 0.9rem 1.5rem;
      background: var(--panel);
      border-bottom: 1px solid var(--border);
    }
    .brand { font-weight: 600; }
    nav { display: flex; gap: 1.25rem; }
    nav a { color: var(--muted); }
    nav a.active { color: var(--text); }
    main { padding: 1.5rem; max-width: 1200px; margin: 0 auto; }
  `,
})
export class AppComponent {}
