import { axe, toHaveNoViolations } from 'jest-axe';
import { definePrimitives } from '../../src/components/primitives';
import { defineStatusCard } from '../../src/components/StatusCard';

expect.extend(toHaveNoViolations);

describe('component accessibility smoke tests', () => {
  beforeAll(() => {
    definePrimitives();
    defineStatusCard();
  });

  it('renders the status card without automated WCAG violations', async () => {
    document.body.innerHTML = `
      <main>
        <ds-status-card
          heading="Accessibility baseline"
          description="Automated checks run with jest-axe."
          status="success"
        ></ds-status-card>
      </main>
    `;

    const results = await axe(document.body);
    expect(results).toHaveNoViolations();
  });

  it('renders primitive controls without automated WCAG violations', async () => {
    document.body.innerHTML = `
      <main>
        <ds-alert class="alert-success">Saved</ds-alert>
        <ds-button>Save</ds-button>
        <ds-badge class="status-approved">approved</ds-badge>
      </main>
    `;

    const results = await axe(document.body);
    expect(results).toHaveNoViolations();
  });
});
