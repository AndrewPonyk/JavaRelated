import { describe, expect, it } from 'vitest';

import { validateEmail, validatePassword, validateRequired } from './validation';

describe('validation helpers', () => {
  it('validates required values', () => {
    expect(validateRequired('', 'Post body')).toBe('Post body is required');
    expect(validateRequired('hello', 'Post body')).toBe('');
  });

  it('validates email shape', () => {
    expect(validateEmail('bad')).toBe('Enter a valid email address');
    expect(validateEmail('demo@example.com')).toBe('');
  });

  it('validates password length', () => {
    expect(validatePassword('short')).toBe('Password must be at least 8 characters');
    expect(validatePassword('password123')).toBe('');
  });
});
