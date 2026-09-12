import { render, screen } from '@testing-library/react';

import { BalanceTrendCard } from './BalanceTrendCard';

describe('BalanceTrendCard', () => {
  it('describes an upward trend accessibly', () => {
    render(
      <BalanceTrendCard
        history={[
          { month: '2026-05', balance: 100 },
          { month: '2026-06', balance: 150 },
        ]}
        currency="EUR"
      />,
    );

    expect(
      screen.getByRole('img', { name: /balance up .*50.* since 2026-05/i }),
    ).toBeInTheDocument();
  });

  it('describes a downward trend accessibly', () => {
    render(
      <BalanceTrendCard
        history={[
          { month: '2026-05', balance: 200 },
          { month: '2026-06', balance: 120 },
        ]}
        currency="EUR"
      />,
    );

    expect(
      screen.getByRole('img', { name: /balance down .*80.* since 2026-05/i }),
    ).toBeInTheDocument();
  });

  it('renders nothing when there are not enough points for a trend', () => {
    const { container } = render(
      <BalanceTrendCard history={[{ month: '2026-06', balance: 100 }]} currency="EUR" />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('handles a perfectly flat history without dividing by zero', () => {
    render(
      <BalanceTrendCard
        history={[
          { month: '2026-05', balance: 100 },
          { month: '2026-06', balance: 100 },
        ]}
        currency="EUR"
      />,
    );

    expect(screen.getByRole('img')).toBeInTheDocument();
  });
});
