import type { Meta, StoryObj } from '@storybook/web-components';
import { html } from 'lit';
import { definePrimitives } from './primitives';

definePrimitives();

const meta: Meta = {
  title: 'Components/Primitives',
  tags: ['autodocs']
};

export default meta;

type Story = StoryObj;

export const ControlsAndFeedback: Story = {
  render: () => html`
    <div style="display: grid; gap: 1rem; max-width: 720px;">
      <div style="display: flex; gap: 0.5rem; flex-wrap: wrap;">
        <ds-button>Save</ds-button>
        <ds-badge class="status-approved">approved</ds-badge>
        <ds-badge class="status-draft">draft</ds-badge>
      </div>
      <ds-alert class="alert-success">Component primitives render with semantic roles.</ds-alert>
      <ds-tabs>
        <div role="tablist" aria-label="Primitive tabs">
          <button role="tab" aria-selected="true" aria-controls="panel-one" id="tab-one">
            Details
          </button>
          <button role="tab" aria-selected="false" aria-controls="panel-two" id="tab-two">
            Usage
          </button>
        </div>
        <div role="tabpanel" id="panel-one" aria-labelledby="tab-one">
          Accessible tab panel content.
        </div>
        <div role="tabpanel" id="panel-two" aria-labelledby="tab-two" hidden>
          Keyboard support uses arrow keys.
        </div>
      </ds-tabs>
    </div>
  `
};
