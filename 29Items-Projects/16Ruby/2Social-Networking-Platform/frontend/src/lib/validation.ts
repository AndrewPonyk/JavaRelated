export function validateRequired(value: string, label: string) {
  return value.trim().length > 0 ? '' : `${label} is required`;
}

export function validateEmail(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim()) ? '' : 'Enter a valid email address';
}

export function validatePassword(value: string) {
  return value.length >= 8 ? '' : 'Password must be at least 8 characters';
}
