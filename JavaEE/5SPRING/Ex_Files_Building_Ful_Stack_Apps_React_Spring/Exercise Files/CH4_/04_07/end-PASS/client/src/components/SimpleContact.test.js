import React from 'react';
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import SingleContact from './SingleContact';

describe('SingleContact Component', () => {
  const mockContact = {
    firstName: 'John',
    lastName: 'Doe',
    email: 'john.doe@example.com'
  };

  it('renders contact name correctly', () => {
    render(<SingleContact item={mockContact} />);
    expect(screen.getByText('John Doe')).toBeInTheDocument();
  });

  it('renders contact email correctly', () => {
    render(<SingleContact item={mockContact} />);
    expect(screen.getByText('john.doe@example.com')).toBeInTheDocument();
  });

  it('has the correct card classes', () => {
    const { container } = render(<SingleContact item={mockContact} />);
    expect(container.querySelector('.card.blue-grey.darken-1')).toBeInTheDocument();
    expect(container.querySelector('.card-content.white-text')).toBeInTheDocument();
  });

  it('test component contains title', ()=>{
   console.log("============passed");
    const { container } = render(<SingleContact item={mockContact} />);
    expect(container).toHaveTextContent('Email:'); // 1 way to check text is present
    expect(screen.getByText(/email:/i)).toBeInTheDocument(); //2nd way
  });
});
//In Jest, it is a function that defines an individual test case. It's actually an alias for test() -
// they're completely interchangeable.
