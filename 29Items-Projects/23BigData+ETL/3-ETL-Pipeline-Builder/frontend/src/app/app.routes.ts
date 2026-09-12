import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
    title: 'Live metrics — ETL Pipeline Builder',
  },
  {
    path: 'pipelines',
    loadComponent: () =>
      import('./features/pipelines/pipeline-list.component').then((m) => m.PipelineListComponent),
    title: 'Pipelines — ETL Pipeline Builder',
  },
  {
    path: 'alerts',
    loadComponent: () =>
      import('./features/alerts/alert-feed.component').then((m) => m.AlertFeedComponent),
    title: 'Anomaly alerts — ETL Pipeline Builder',
  },
  { path: '**', redirectTo: '' },
];
