import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/Button';

export const Privacy: React.FC = () => {
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
          <h1 className="text-3xl font-bold text-gray-900 mb-2">Privacy Policy</h1>
          <p className="text-sm text-gray-500 mb-8">Last updated: February 3, 2026</p>

          <div className="prose prose-gray max-w-none">
            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">1. Introduction</h2>
              <p className="text-gray-600 mb-4">
                Enterprise Dashboard Platform ("we", "our", or "us") is committed to protecting your privacy.
                This Privacy Policy explains how we collect, use, disclose, and safeguard your information when
                you use our Service.
              </p>
              <p className="text-gray-600 mb-4">
                Please read this Privacy Policy carefully. By using the Service, you agree to the collection
                and use of information in accordance with this policy.
              </p>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">2. Information We Collect</h2>

              <h3 className="text-lg font-medium text-gray-800 mb-3">2.1 Personal Information</h3>
              <p className="text-gray-600 mb-4">We may collect personally identifiable information, including but not limited to:</p>
              <ul className="list-disc pl-6 text-gray-600 mb-4 space-y-2">
                <li>Name (first and last name)</li>
                <li>Email address</li>
                <li>Profile picture</li>
                <li>Job title and company name</li>
                <li>Contact preferences</li>
              </ul>

              <h3 className="text-lg font-medium text-gray-800 mb-3">2.2 Usage Data</h3>
              <p className="text-gray-600 mb-4">We automatically collect certain information when you use the Service:</p>
              <ul className="list-disc pl-6 text-gray-600 mb-4 space-y-2">
                <li>IP address and browser type</li>
                <li>Pages visited and time spent on pages</li>
                <li>Features used and actions taken</li>
                <li>Device information and operating system</li>
                <li>Referring website addresses</li>
              </ul>

              <h3 className="text-lg font-medium text-gray-800 mb-3">2.3 Dashboard and Widget Data</h3>
              <p className="text-gray-600 mb-4">
                We collect and store the dashboards, widgets, and configurations you create within the Service.
                This includes data source connections, visualization settings, and sharing preferences.
              </p>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">3. How We Use Your Information</h2>
              <p className="text-gray-600 mb-4">We use the information we collect to:</p>
              <ul className="list-disc pl-6 text-gray-600 mb-4 space-y-2">
                <li>Provide, maintain, and improve the Service</li>
                <li>Process and complete transactions</li>
                <li>Send you technical notices and support messages</li>
                <li>Respond to your comments, questions, and requests</li>
                <li>Monitor and analyze usage patterns and trends</li>
                <li>Detect, investigate, and prevent security incidents</li>
                <li>Personalize and improve your experience</li>
                <li>Comply with legal obligations</li>
              </ul>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">4. Information Sharing and Disclosure</h2>
              <p className="text-gray-600 mb-4">We may share your information in the following situations:</p>
              <ul className="list-disc pl-6 text-gray-600 mb-4 space-y-2">
                <li><strong>With your consent:</strong> We may share information when you give us permission</li>
                <li><strong>With team members:</strong> When you share dashboards or collaborate with others</li>
                <li><strong>Service providers:</strong> With third parties who perform services on our behalf</li>
                <li><strong>Legal requirements:</strong> When required by law or to protect our rights</li>
                <li><strong>Business transfers:</strong> In connection with a merger, acquisition, or sale of assets</li>
              </ul>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">5. Data Security</h2>
              <p className="text-gray-600 mb-4">
                We implement appropriate technical and organizational security measures to protect your personal
                information, including:
              </p>
              <ul className="list-disc pl-6 text-gray-600 mb-4 space-y-2">
                <li>Encryption of data in transit and at rest</li>
                <li>Regular security assessments and audits</li>
                <li>Access controls and authentication mechanisms</li>
                <li>Secure development practices</li>
                <li>Employee security training</li>
              </ul>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">6. Data Retention</h2>
              <p className="text-gray-600 mb-4">
                We retain your personal information for as long as necessary to fulfill the purposes outlined
                in this Privacy Policy, unless a longer retention period is required or permitted by law.
                When you delete your account, we will delete or anonymize your information within 30 days,
                except where we are required to retain it for legal or legitimate business purposes.
              </p>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">7. Your Privacy Rights</h2>
              <p className="text-gray-600 mb-4">Depending on your location, you may have the right to:</p>
              <ul className="list-disc pl-6 text-gray-600 mb-4 space-y-2">
                <li><strong>Access:</strong> Request a copy of your personal information</li>
                <li><strong>Rectification:</strong> Request correction of inaccurate information</li>
                <li><strong>Erasure:</strong> Request deletion of your personal information</li>
                <li><strong>Portability:</strong> Request a transfer of your data</li>
                <li><strong>Restriction:</strong> Request limitation of processing</li>
                <li><strong>Objection:</strong> Object to certain processing activities</li>
              </ul>
              <p className="text-gray-600 mb-4">
                To exercise any of these rights, please contact us using the information provided below.
              </p>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">8. Cookies and Tracking Technologies</h2>
              <p className="text-gray-600 mb-4">
                We use cookies and similar tracking technologies to track activity on our Service and hold
                certain information. Cookies are files with a small amount of data that may include an
                anonymous unique identifier.
              </p>
              <p className="text-gray-600 mb-4">Types of cookies we use:</p>
              <ul className="list-disc pl-6 text-gray-600 mb-4 space-y-2">
                <li><strong>Essential cookies:</strong> Required for the Service to function properly</li>
                <li><strong>Preference cookies:</strong> Remember your settings and preferences</li>
                <li><strong>Analytics cookies:</strong> Help us understand how you use the Service</li>
              </ul>
              <p className="text-gray-600 mb-4">
                You can instruct your browser to refuse all cookies or to indicate when a cookie is being sent.
                However, if you do not accept cookies, you may not be able to use some portions of our Service.
              </p>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">9. Children's Privacy</h2>
              <p className="text-gray-600 mb-4">
                Our Service is not intended for use by children under the age of 13. We do not knowingly
                collect personally identifiable information from children under 13. If we discover that a
                child under 13 has provided us with personal information, we will delete such information
                from our servers immediately.
              </p>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">10. International Data Transfers</h2>
              <p className="text-gray-600 mb-4">
                Your information may be transferred to and maintained on computers located outside of your
                state, province, country, or other governmental jurisdiction where the data protection laws
                may differ. We ensure appropriate safeguards are in place to protect your information in
                accordance with this Privacy Policy.
              </p>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">11. Changes to This Privacy Policy</h2>
              <p className="text-gray-600 mb-4">
                We may update our Privacy Policy from time to time. We will notify you of any changes by
                posting the new Privacy Policy on this page and updating the "Last updated" date. You are
                advised to review this Privacy Policy periodically for any changes.
              </p>
            </section>

            <section className="mb-8">
              <h2 className="text-xl font-semibold text-gray-900 mb-4">12. Contact Us</h2>
              <p className="text-gray-600 mb-4">
                If you have any questions about this Privacy Policy or our data practices, please contact us:
              </p>
              <p className="text-gray-600">
                <strong>Email:</strong> privacy@enterprisedashboard.com<br />
                <strong>Address:</strong> 123 Dashboard Lane, Suite 100, Tech City, TC 12345<br />
                <strong>Data Protection Officer:</strong> dpo@enterprisedashboard.com
              </p>
            </section>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Privacy;
