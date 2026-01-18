import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full'
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./components/dashboard/dashboard.component')
      .then(m => m.DashboardComponent)
  },
  {
    path: 'claims',
    loadComponent: () => import('./components/claim-list/claim-list.component')
      .then(m => m.ClaimListComponent)
  },
  {
    path: 'claims/:id',
    loadComponent: () => import('./components/claim-detail/claim-detail.component')
      .then(m => m.ClaimDetailComponent)
  },
  {
    path: 'review-queue',
    loadComponent: () => import('./components/claim-list/claim-list.component')
      .then(m => m.ClaimListComponent),
    data: { reviewQueue: true }
  },
  {
    path: 'reports',
    loadComponent: () => import('./components/dashboard/dashboard.component')
      .then(m => m.DashboardComponent)
  },
  {
    path: '**',
    redirectTo: 'dashboard'
  }
];
