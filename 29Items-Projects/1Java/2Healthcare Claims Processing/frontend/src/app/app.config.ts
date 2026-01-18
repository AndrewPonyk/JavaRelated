import { ApplicationConfig } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { routes } from './app.routes';

// TODO: Configure Apollo Client for GraphQL
// import { provideApollo } from 'apollo-angular';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(
      withInterceptors([
        // TODO: Add auth interceptor
      ])
    ),
    provideAnimations(),
    // TODO: Add Apollo GraphQL provider
  ]
};
