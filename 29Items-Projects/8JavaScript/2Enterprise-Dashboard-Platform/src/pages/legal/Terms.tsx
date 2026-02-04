import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/Button';

export const Terms: React.FC = () => {
  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-4xl mx-auto px-4 py-12">
        <div className="mb-8">
          <Link to="/auth/register">
            <Button variant="ghost" size="sm">
              <ArrowLeft className="mr-2 h-4 w-4" />
              Back to Registration
            </Button>
          </Link>
        </div>

        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-8">
          <h1 className="text-3xl font-bold text-gray-900 mb-2">Terms of Service</h1>
          <p className="text-sm text-gray-500 mb-8">Last updated: February 3, 2026</p>

          <div className="prose prose-gray max-w-none">
            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">1. Acceptance of Terms</h2>
              <p className="text-gray-600 mb-4">
                By accessing or using the Enterprise Dashboard Platform ("Service"), you agree to be bound by these
                Terms of Service ("Terms"). If you disagree with any part of the terms, you may not access the Service.
              </p>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">2. Description of Service</h2>
              <p className="text-gray-600 mb-4">
                Enterprise Dashboard Platform provides a comprehensive dashboard management system that allows users to:
              </p>
              <ul className="list-disc pl-6 text-gray-600 mb-4 space-y-2">
                <li>Create and customize interactive dashboards</li>
                <li>Build and configure data visualization widgets</li>
                <li>Share dashboards and collaborate with team members</li>
                <li>Connect to various data sources</li>
                <li>Export reports in multiple formats</li>
              </ul>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">3. User Accounts</h2>
              <p className="text-gray-600 mb-4">
                When you create an account with us, you must provide accurate, complete, and current information.
                Failure to do so constitutes a breach of the Terms, which may result in immediate termination of
                your account on our Service.
              </p>
              <p className="text-gray-600 mb-4">
                You are responsible for safeguarding the password that you use to access the Service and for any
                activities or actions under your password, whether your password is with our Service or a third-party service.
              </p>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">4. Acceptable Use</h2>
              <p className="text-gray-600 mb-4">You agree not to use the Service to:</p>
              <ul className="list-disc pl-6 text-gray-600 mb-4 space-y-2">
                <li>Violate any applicable laws or regulations</li>
                <li>Infringe upon the rights of others</li>
                <li>Transmit malicious code or interfere with the Service</li>
                <li>Attempt to gain unauthorized access to any portion of the Service</li>
                <li>Use the Service for any illegal or unauthorized purpose</li>
              </ul>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">5. Intellectual Property</h2>
              <p className="text-gray-600 mb-4">
                The Service and its original content, features, and functionality are and will remain the exclusive
                property of Enterprise Dashboard Platform and its licensors. The Service is protected by copyright,
                trademark, and other laws of both the United States and foreign countries.
              </p>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">6. User Content</h2>
              <p className="text-gray-600 mb-4">
                You retain all rights to any content you submit, post, or display on or through the Service.
                By posting content, you grant us a license to use, modify, and display that content in connection
                with providing the Service.
              </p>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">7. Data Security</h2>
              <p className="text-gray-600 mb-4">
                We implement appropriate technical and organizational measures to protect your data. However,
                no method of transmission over the Internet or electronic storage is 100% secure. While we strive
                to use commercially acceptable means to protect your data, we cannot guarantee its absolute security.
              </p>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">8. Termination</h2>
              <p className="text-gray-600 mb-4">
                We may terminate or suspend your account immediately, without prior notice or liability, for any
                reason whatsoever, including without limitation if you breach the Terms. Upon termination, your
                right to use the Service will immediately cease.
              </p>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">9. Limitation of Liability</h2>
              <p className="text-gray-600 mb-4">
                In no event shall Enterprise Dashboard Platform, nor its directors, employees, partners, agents,
                suppliers, or affiliates, be liable for any indirect, incidental, special, consequential, or
                punitive damages, including without limitation, loss of profits, data, use, goodwill, or other
                intangible losses, resulting from your access to or use of or inability to access or use the Service.
              </p>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">10. Changes to Terms</h2>
              <p className="text-gray-600 mb-4">
                We reserve the right, at our sole discretion, to modify or replace these Terms at any time.
                If a revision is material, we will try to provide at least 30 days notice prior to any new
                terms taking effect. What constitutes a material change will be determined at our sole discretion.
              </p>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">11. Contact Us</h2>
              <p className="text-gray-600 mb-4">
                If you have any questions about these Terms, please contact us at:
              </p>
              <p className="text-gray-600">
                <strong>Email:</strong> legal@enterprisedashboard.com<br />
                <strong>Address:</strong> 123 Dashboard Lane, Suite 100, Tech City, TC 12345
              </p>
            </section>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Terms;
