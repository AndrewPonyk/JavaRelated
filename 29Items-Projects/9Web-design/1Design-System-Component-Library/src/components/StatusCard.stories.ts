import type { Meta, StoryObj } from '@storybook/web-components';
import { html } from 'lit';
import './StatusCard';

const meta: Meta = {
  title: 'Components/Status Card',
  component: 'ds-status-card',
  tags: ['autodocs'],
  argTypes: {
    heading: { control: 'text' },
    description: { control: 'text' },
    status: {
      control: 'select',
      options: ['success', 'warning', 'danger']
    }
  }
};

export default meta;

type Story = StoryObj;

export const Default: Story = {
  args: {
    heading: 'Token sync completed',
    description: 'Figma token export matches the package source.',
    status: 'success'
  },
  render: ({ heading, description, status }) => html`
    <ds-status-card heading=${heading} description=${description} status=${status}></ds-status-card>
  `
};

export const Warning: Story = {
  args: {
    heading: 'Color contrast review needed',
    description: 'One semantic color pair needs manual WCAG 2.1 AA verification.',
    status: 'warning'
  },
  render: ({ heading, description, status }) => html`
    <ds-status-card heading=${heading} description=${description} status=${status}></ds-status-card>
  `
};
