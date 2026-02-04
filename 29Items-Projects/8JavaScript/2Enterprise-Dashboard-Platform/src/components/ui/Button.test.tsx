import { describe, it, expect, vi } from 'vitest';
import { render, screen, userEvent } from '~/test/test-utils';
import { Button } from './Button';

describe('Button', () => {
  it('renders correctly with default props', () => {
    render(<Button>Click me</Button>);

    const button = screen.getByRole('button', { name: /click me/i });
    expect(button).toBeInTheDocument();
    expect(button).toHaveClass('bg-blue-600');
  });

  it('applies variant styles correctly', () => {
    render(<Button variant="outline">Outline Button</Button>);

    const button = screen.getByRole('button');
    expect(button).toHaveClass('border-gray-300');
    expect(button).not.toHaveClass('bg-blue-600');
  });

  it('applies size styles correctly', () => {
    render(<Button size="sm">Small Button</Button>);

    const button = screen.getByRole('button');
    expect(button).toHaveClass('px-2', 'py-1', 'text-sm');
  });

  it('handles disabled state correctly', () => {
    render(<Button disabled>Disabled Button</Button>);

    const button = screen.getByRole('button');
    expect(button).toBeDisabled();
    expect(button).toHaveClass('opacity-50', 'cursor-not-allowed');
  });

  it('shows loading state correctly', () => {
    render(<Button loading>Loading Button</Button>);

    const button = screen.getByRole('button');
    expect(button).toBeDisabled();
    expect(screen.getByRole('status')).toBeInTheDocument(); // Loading spinner
  });

  it('calls onClick handler when clicked', async () => {
    const user = userEvent.setup();
    const handleClick = vi.fn();

    render(<Button onClick={handleClick}>Click me</Button>);

    const button = screen.getByRole('button');
    await user.click(button);

    expect(handleClick).toHaveBeenCalledOnce();
  });

  it('does not call onClick when disabled', async () => {
    const user = userEvent.setup();
    const handleClick = vi.fn();

    render(
      <Button onClick={handleClick} disabled>
        Disabled Button
      </Button>
    );

    const button = screen.getByRole('button');
    await user.click(button);

    expect(handleClick).not.toHaveBeenCalled();
  });

  it('renders with left icon', () => {
    const TestIcon = () => <span data-testid="left-icon">Icon</span>;

    render(
      <Button leftIcon={<TestIcon />}>
        Button with Icon
      </Button>
    );

    expect(screen.getByTestId('left-icon')).toBeInTheDocument();
  });

  it('renders with right icon', () => {
    const TestIcon = () => <span data-testid="right-icon">Icon</span>;

    render(
      <Button rightIcon={<TestIcon />}>
        Button with Icon
      </Button>
    );

    expect(screen.getByTestId('right-icon')).toBeInTheDocument();
  });

  it('applies custom className', () => {
    render(<Button className="custom-class">Button</Button>);

    const button = screen.getByRole('button');
    expect(button).toHaveClass('custom-class');
  });

  it('forwards ref correctly', () => {
    const ref = vi.fn();

    render(<Button ref={ref}>Button</Button>);

    expect(ref).toHaveBeenCalled();
  });
});