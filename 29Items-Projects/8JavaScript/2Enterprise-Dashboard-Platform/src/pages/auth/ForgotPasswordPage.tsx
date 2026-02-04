import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { Mail, AlertCircle, CheckCircle, ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useToastHelpers } from '@/components/ui/Toaster';
import { authApi } from '@/services/api/authApi';
import { cn } from '@/utils/cn';

export const ForgotPasswordPage: React.FC = () => {
  const [email, setEmail] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [error, setError] = useState('');

  const { error: showError, success: showSuccess } = useToastHelpers();

  const validateEmail = (email: string) => {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!email) {
      setError('Email is required');
      return;
    }

    if (!validateEmail(email)) {
      setError('Please enter a valid email address');
      return;
    }

    setIsLoading(true);

    try {
      await authApi.forgotPassword(email);
      setIsSubmitted(true);
      showSuccess('Email sent', 'Check your inbox for password reset instructions');
    } catch (err: any) {
      const errorMessage = err.message || 'Failed to send reset email. Please try again.';
      setError(errorMessage);
      showError('Error', errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  if (isSubmitted) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
        <div className="max-w-md w-full space-y-8">
          <div className="text-center">
            <div className="mx-auto flex items-center justify-center h-16 w-16 rounded-full bg-green-100 mb-6">
              <CheckCircle className="h-8 w-8 text-green-600" />
            </div>
            <h2 className="text-3xl font-bold text-gray-900 mb-2">Check your email</h2>
            <p className="text-gray-600 mb-6">
              We've sent password reset instructions to <strong>{email}</strong>
            </p>
            <div className="space-y-4">
              <p className="text-sm text-gray-500">
                Didn't receive the email? Check your spam folder or{' '}
                <button
                  onClick={() => setIsSubmitted(false)}
                  className="text-blue-600 hover:text-blue-500 font-medium"
                >
                  try again
                </button>
              </p>
              <Link to="/auth/login">
                <Button variant="outline" className="w-full">
                  <ArrowLeft className="mr-2 h-4 w-4" />
                  Back to login
                </Button>
              </Link>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full space-y-8">
        <div className="text-center">
          <div className="mx-auto flex items-center justify-center h-16 w-16 rounded-lg bg-blue-600 text-white mb-6">
            <span className="text-xl font-bold">ED</span>
          </div>
          <h2 className="text-3xl font-bold text-gray-900 mb-2">Forgot password?</h2>
          <p className="text-gray-600">
            No worries, we'll send you reset instructions.
          </p>
        </div>

        <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
          {error && (
            <div className="rounded-md bg-red-50 border border-red-200 p-4">
              <div className="flex">
                <AlertCircle className="h-5 w-5 text-red-400" />
                <p className="ml-3 text-sm text-red-700">{error}</p>
              </div>
            </div>
          )}

          <div>
            <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-2">
              Email address
            </label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <Mail className="h-5 w-5 text-gray-400" />
              </div>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className={cn(
                  'block w-full pl-10 pr-3 py-2 border rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-blue-500 focus:border-blue-500',
                  error ? 'border-red-300' : 'border-gray-300'
                )}
                placeholder="Enter your email"
              />
            </div>
          </div>

          <Button type="submit" disabled={isLoading} className="w-full">
            {isLoading ? (
              <>
                <div className="animate-spin -ml-1 mr-3 h-5 w-5 border-2 border-white border-t-transparent rounded-full" />
                Sending...
              </>
            ) : (
              'Reset password'
            )}
          </Button>

          <div className="text-center">
            <Link to="/auth/login" className="inline-flex items-center text-sm font-medium text-blue-600 hover:text-blue-500">
              <ArrowLeft className="mr-2 h-4 w-4" />
              Back to login
            </Link>
          </div>
        </form>
      </div>
    </div>
  );
};

export default ForgotPasswordPage;
