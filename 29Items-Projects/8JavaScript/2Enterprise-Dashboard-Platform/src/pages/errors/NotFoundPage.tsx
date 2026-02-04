import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Home, ArrowLeft, Search, HelpCircle } from 'lucide-react';
import { Button } from '@/components/ui/Button';

export const NotFoundPage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="max-w-md w-full text-center px-4">
        {/* 404 Illustration */}
        <div className="mb-8">
          <div className="text-8xl font-bold text-gray-300 mb-4">404</div>
          <div className="relative">
            <div className="absolute inset-0 flex items-center justify-center">
              <Search className="h-16 w-16 text-gray-300 animate-pulse" />
            </div>
          </div>
        </div>

        {/* Error Message */}
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-gray-900 mb-2">
            Page Not Found
          </h1>
          <p className="text-gray-600 mb-6">
            Sorry, we couldn't find the page you're looking for.
            The page may have been moved, deleted, or the URL might be incorrect.
          </p>
        </div>

        {/* Action Buttons */}
        <div className="space-y-3 mb-8">
          <Button
            onClick={() => navigate(-1)}
            className="w-full"
            variant="outline"
          >
            <ArrowLeft className="mr-2 h-4 w-4" />
            Go Back
          </Button>

          <Link to="/dashboard" className="block w-full">
            <Button className="w-full">
              <Home className="mr-2 h-4 w-4" />
              Back to Dashboard
            </Button>
          </Link>
        </div>

        {/* Help Links */}
        <div className="text-sm text-gray-500 space-y-2">
          <p>Need help? Try these options:</p>
          <div className="flex justify-center space-x-4">
            <Link
              to="/support"
              className="inline-flex items-center text-blue-600 hover:text-blue-700"
            >
              <HelpCircle className="mr-1 h-4 w-4" />
              Contact Support
            </Link>
            <Link
              to="/dashboard"
              className="inline-flex items-center text-blue-600 hover:text-blue-700"
            >
              <Search className="mr-1 h-4 w-4" />
              Search Dashboards
            </Link>
          </div>
        </div>

        {/* Additional Info */}
        <div className="mt-8 p-4 bg-blue-50 rounded-lg text-left">
          <h3 className="text-sm font-medium text-blue-800 mb-2">
            Common Solutions:
          </h3>
          <ul className="text-xs text-blue-700 space-y-1">
            <li>• Check the URL for any typos</li>
            <li>• Try navigating from the main dashboard</li>
            <li>• Use the search function to find what you need</li>
            <li>• Contact your administrator if you believe this is an error</li>
          </ul>
        </div>

        {/* Error Code for Support */}
        <div className="mt-6 text-xs text-gray-400">
          Error Code: 404 | Page Not Found
          <br />
          If contacting support, please reference this error code.
        </div>
      </div>
    </div>
  );
};

export default NotFoundPage;