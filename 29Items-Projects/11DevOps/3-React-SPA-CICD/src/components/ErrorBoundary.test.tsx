import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';

import { ErrorBoundary, RouteErrorFallback } from './ErrorBoundary';

function Bomb(): never {
  throw new Error('kaboom');
}

describe('ErrorBoundary', () => {
  it('renders its children when nothing goes wrong', () => {
    render(
      <ErrorBoundary>
        <p>all good</p>
      </ErrorBoundary>,
    );
    expect(screen.getByText('all good')).toBeInTheDocument();
  });

  it('contains render errors and shows the fallback with a reload affordance', () => {
    // React logs caught errors to console.error — silence the expected noise.
    jest.spyOn(console, 'error').mockImplementation(() => undefined);

    render(
      <ErrorBoundary>
        <Bomb />
      </ErrorBoundary>,
    );

    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent(/something went wrong/i);
    expect(screen.getByRole('button', { name: /reload the page/i })).toBeInTheDocument();
  });

  it('supports a custom fallback', () => {
    jest.spyOn(console, 'error').mockImplementation(() => undefined);

    render(
      <ErrorBoundary fallback={<p>custom fallback</p>}>
        <Bomb />
      </ErrorBoundary>,
    );

    expect(screen.getByText('custom fallback')).toBeInTheDocument();
  });
});

describe('RouteErrorFallback', () => {
  it('renders route error responses with their status', async () => {
    const router = createMemoryRouter([
      {
        path: '/',
        element: <p>never rendered</p>,
        errorElement: <RouteErrorFallback />,
        loader: () => {
          throw new Response('gone', { status: 404, statusText: 'Not Found' });
        },
      },
    ]);

    render(<RouterProvider router={router} />);

    expect(await screen.findByRole('alert')).toHaveTextContent(/404/);
  });

  it('renders unexpected route errors with a retry affordance', async () => {
    jest.spyOn(console, 'error').mockImplementation(() => undefined);

    const router = createMemoryRouter([
      {
        path: '/',
        element: <Bomb />,
        errorElement: <RouteErrorFallback />,
      },
    ]);

    render(<RouterProvider router={router} />);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/something went wrong/i);
    expect(screen.getByRole('button', { name: /reload the page/i })).toBeInTheDocument();
  });
});
