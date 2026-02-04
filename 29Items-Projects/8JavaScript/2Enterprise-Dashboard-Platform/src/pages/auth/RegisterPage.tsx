import React, { useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { Eye, EyeOff, Mail, Lock, User, AlertCircle, CheckCircle } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useAuth } from '@/components/auth/AuthProvider';
import { useToastHelpers } from '@/components/ui/Toaster';
import { cn } from '@/utils/cn';

export const RegisterPage: React.FC = () => {
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    confirmPassword: '',
  });
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [agreedToTerms, setAgreedToTerms] = useState(false);

  const { register, isAuthenticated } = useAuth();
  const { error: showError } = useToastHelpers();

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  const passwordRequirements = [
    { label: 'At least 8 characters', test: (p: string) => p.length >= 8 },
    { label: 'One uppercase letter', test: (p: string) => /[A-Z]/.test(p) },
    { label: 'One lowercase letter', test: (p: string) => /[a-z]/.test(p) },
    { label: 'One number', test: (p: string) => /\d/.test(p) },
  ];

  const validateForm = () => {
    const newErrors: Record<string, string> = {};

    if (!formData.firstName.trim()) {
      newErrors.firstName = 'First name is required';
    }

    if (!formData.email) {
      newErrors.email = 'Email is required';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
      newErrors.email = 'Please enter a valid email address';
    }

    if (!formData.password) {
      newErrors.password = 'Password is required';
    } else if (!passwordRequirements.every(req => req.test(formData.password))) {
      newErrors.password = 'Password does not meet requirements';
    }

    if (formData.password !== formData.confirmPassword) {
      newErrors.confirmPassword = 'Passwords do not match';
    }

    if (!agreedToTerms) {
      newErrors.terms = 'You must agree to the terms and conditions';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    setIsLoading(true);
    setErrors({});

    try {
      await register({
        email: formData.email,
        password: formData.password,
        firstName: formData.firstName,
        lastName: formData.lastName,
      });
    } catch (error: any) {
      const errorMessage = error.message || 'Registration failed. Please try again.';
      setErrors({ general: errorMessage });
      showError('Registration failed', errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  const handleChange = (field: string) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({ ...formData, [field]: e.target.value });
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full space-y-8">
        <div className="text-center">
          <div className="mx-auto flex items-center justify-center h-16 w-16 rounded-lg bg-blue-600 text-white mb-6">
            <span className="text-xl font-bold">ED</span>
          </div>
          <h2 className="text-3xl font-bold text-gray-900 mb-2">Create your account</h2>
          <p className="text-gray-600">Get started with Enterprise Dashboard</p>
        </div>

        <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
          {errors.general && (
            <div className="rounded-md bg-red-50 border border-red-200 p-4">
              <div className="flex">
                <AlertCircle className="h-5 w-5 text-red-400" />
                <p className="ml-3 text-sm text-red-700">{errors.general}</p>
              </div>
            </div>
          )}

          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label htmlFor="firstName" className="block text-sm font-medium text-gray-700 mb-2">
                  First name *
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    <User className="h-5 w-5 text-gray-400" />
                  </div>
                  <input
                    id="firstName"
                    type="text"
                    value={formData.firstName}
                    onChange={handleChange('firstName')}
                    className={cn(
                      'block w-full pl-10 pr-3 py-2 border rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-blue-500 focus:border-blue-500',
                      errors.firstName ? 'border-red-300' : 'border-gray-300'
                    )}
                    placeholder="John"
                  />
                </div>
                {errors.firstName && <p className="mt-1 text-sm text-red-600">{errors.firstName}</p>}
              </div>

              <div>
                <label htmlFor="lastName" className="block text-sm font-medium text-gray-700 mb-2">
                  Last name
                </label>
                <input
                  id="lastName"
                  type="text"
                  value={formData.lastName}
                  onChange={handleChange('lastName')}
                  className="block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-blue-500 focus:border-blue-500"
                  placeholder="Doe"
                />
              </div>
            </div>

            <div>
              <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-2">
                Email address *
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <Mail className="h-5 w-5 text-gray-400" />
                </div>
                <input
                  id="email"
                  type="email"
                  value={formData.email}
                  onChange={handleChange('email')}
                  className={cn(
                    'block w-full pl-10 pr-3 py-2 border rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-blue-500 focus:border-blue-500',
                    errors.email ? 'border-red-300' : 'border-gray-300'
                  )}
                  placeholder="john@example.com"
                />
              </div>
              {errors.email && <p className="mt-1 text-sm text-red-600">{errors.email}</p>}
            </div>

            <div>
              <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-2">
                Password *
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <Lock className="h-5 w-5 text-gray-400" />
                </div>
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  value={formData.password}
                  onChange={handleChange('password')}
                  className={cn(
                    'block w-full pl-10 pr-10 py-2 border rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-blue-500 focus:border-blue-500',
                    errors.password ? 'border-red-300' : 'border-gray-300'
                  )}
                  placeholder="Create a password"
                />
                <button
                  type="button"
                  className="absolute inset-y-0 right-0 pr-3 flex items-center"
                  onClick={() => setShowPassword(!showPassword)}
                >
                  {showPassword ? <EyeOff className="h-5 w-5 text-gray-400" /> : <Eye className="h-5 w-5 text-gray-400" />}
                </button>
              </div>
              <div className="mt-2 space-y-1">
                {passwordRequirements.map((req, i) => (
                  <div key={i} className="flex items-center text-xs">
                    <CheckCircle className={cn('h-3 w-3 mr-2', req.test(formData.password) ? 'text-green-500' : 'text-gray-300')} />
                    <span className={req.test(formData.password) ? 'text-green-600' : 'text-gray-500'}>{req.label}</span>
                  </div>
                ))}
              </div>
            </div>

            <div>
              <label htmlFor="confirmPassword" className="block text-sm font-medium text-gray-700 mb-2">
                Confirm password *
              </label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <Lock className="h-5 w-5 text-gray-400" />
                </div>
                <input
                  id="confirmPassword"
                  type={showConfirmPassword ? 'text' : 'password'}
                  value={formData.confirmPassword}
                  onChange={handleChange('confirmPassword')}
                  className={cn(
                    'block w-full pl-10 pr-10 py-2 border rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-blue-500 focus:border-blue-500',
                    errors.confirmPassword ? 'border-red-300' : 'border-gray-300'
                  )}
                  placeholder="Confirm your password"
                />
                <button
                  type="button"
                  className="absolute inset-y-0 right-0 pr-3 flex items-center"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                >
                  {showConfirmPassword ? <EyeOff className="h-5 w-5 text-gray-400" /> : <Eye className="h-5 w-5 text-gray-400" />}
                </button>
              </div>
              {errors.confirmPassword && <p className="mt-1 text-sm text-red-600">{errors.confirmPassword}</p>}
            </div>
          </div>

          <div className="flex items-start">
            <input
              id="terms"
              type="checkbox"
              checked={agreedToTerms}
              onChange={(e) => setAgreedToTerms(e.target.checked)}
              className="h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300 rounded mt-1"
            />
            <label htmlFor="terms" className="ml-2 block text-sm text-gray-700">
              I agree to the <Link to="/legal/terms" className="text-blue-600 hover:text-blue-500">Terms of Service</Link> and{' '}
              <Link to="/legal/privacy" className="text-blue-600 hover:text-blue-500">Privacy Policy</Link>
            </label>
          </div>
          {errors.terms && <p className="text-sm text-red-600">{errors.terms}</p>}

          <Button type="submit" disabled={isLoading} className="w-full">
            {isLoading ? (
              <>
                <div className="animate-spin -ml-1 mr-3 h-5 w-5 border-2 border-white border-t-transparent rounded-full" />
                Creating account...
              </>
            ) : (
              'Create account'
            )}
          </Button>

          <div className="text-center">
            <p className="text-sm text-gray-600">
              Already have an account?{' '}
              <Link to="/auth/login" className="font-medium text-blue-600 hover:text-blue-500">Sign in</Link>
            </p>
          </div>
        </form>
      </div>
    </div>
  );
};

export default RegisterPage;
