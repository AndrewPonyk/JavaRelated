import { cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import type { InteractionCheckResponse } from '../types';
import { InteractionResult } from './InteractionResult';

afterEach(cleanup);

describe('InteractionResult', () => {
  it('renders the empty state', () => {
    const result: InteractionCheckResponse = {
      checked_drugs: [],
      unresolved: [],
      interactions: [],
      highest_severity: 'unknown',
    };
    const { container } = render(<InteractionResult result={result} />);
    expect(container.textContent).toContain('No known interactions');
  });

  it('renders an interaction with its severity label', () => {
    const result: InteractionCheckResponse = {
      checked_drugs: ['warfarin', 'aspirin'],
      unresolved: [],
      interactions: [
        {
          rxcui_a: '11289',
          rxcui_b: '1191',
          name_a: 'warfarin',
          name_b: 'aspirin',
          severity: 'major',
          description: 'Bleeding risk.',
          ml_predicted: false,
        },
      ],
      highest_severity: 'major',
    };
    const { container } = render(<InteractionResult result={result} />);
    expect(container.textContent).toContain('warfarin + aspirin');
    expect(container.textContent).toContain('Major');
    expect(container.textContent).toContain('Bleeding risk.');
  });

  it('shows a warning for unresolved drugs', () => {
    const result: InteractionCheckResponse = {
      checked_drugs: [],
      unresolved: ['not-a-drug'],
      interactions: [],
      highest_severity: 'unknown',
    };
    const { container } = render(<InteractionResult result={result} />);
    expect(container.textContent).toContain('Could not resolve');
  });
});
