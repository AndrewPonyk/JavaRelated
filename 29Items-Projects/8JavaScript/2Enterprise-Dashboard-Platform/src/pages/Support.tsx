import React, { useState } from 'react';
import { HelpCircle, Book, MessageCircle, Mail, ExternalLink, Search, ChevronRight } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';

const faqItems = [
  { id: '1', question: 'How do I create a new dashboard?', answer: 'Click the "New Dashboard" button in the sidebar or on the overview page. You can then add widgets and customize the layout.' },
  { id: '2', question: 'Can I share dashboards with my team?', answer: 'Yes! Click the share button on any dashboard to invite team members or generate a shareable link.' },
  { id: '3', question: 'How do I connect a data source?', answer: 'Go to Data Sources in the sidebar, click "Add Data Source", and follow the setup wizard for your database or API.' },
  { id: '4', question: 'What widget types are available?', answer: 'We offer charts (line, bar, pie), metrics, tables, maps, and text widgets. Check the Widget Templates for more options.' },
  { id: '5', question: 'How do I export my data?', answer: 'Use the Reports section to generate and download reports in various formats including PDF, CSV, and Excel.' },
];

const resources = [
  { title: 'Documentation', description: 'Complete guides and API reference', icon: Book, href: '#' },
  { title: 'Video Tutorials', description: 'Step-by-step video guides', icon: ExternalLink, href: '#' },
  { title: 'Community Forum', description: 'Connect with other users', icon: MessageCircle, href: '#' },
  { title: 'API Reference', description: 'Technical documentation', icon: ExternalLink, href: '#' },
];

export const Support: React.FC = () => {
  const [search, setSearch] = useState('');
  const [expandedFaq, setExpandedFaq] = useState<string | null>(null);
  const [contactForm, setContactForm] = useState({ subject: '', message: '' });

  const filteredFaq = faqItems.filter(item =>
    item.question.toLowerCase().includes(search.toLowerCase()) ||
    item.answer.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Help & Support</h1>
          <p className="text-gray-600">Find answers and get help</p>
        </div>
      </div>

      {/* Search */}
      <Card className="p-6">
        <div className="max-w-2xl mx-auto text-center">
          <HelpCircle className="h-12 w-12 mx-auto mb-4 text-blue-500" />
          <h2 className="text-xl font-semibold text-gray-900 mb-2">How can we help you?</h2>
          <div className="relative mt-4">
            <Search className="absolute left-4 top-1/2 transform -translate-y-1/2 h-5 w-5 text-gray-400" />
            <input
              type="text"
              placeholder="Search for help..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-12 pr-4 py-3 border border-gray-300 rounded-lg text-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>
      </Card>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* FAQ */}
        <div className="lg:col-span-2">
          <Card className="p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">Frequently Asked Questions</h2>
            <div className="space-y-2">
              {filteredFaq.map((item) => (
                <div key={item.id} className="border rounded-lg">
                  <button
                    onClick={() => setExpandedFaq(expandedFaq === item.id ? null : item.id)}
                    className="w-full flex items-center justify-between p-4 text-left"
                  >
                    <span className="font-medium text-gray-900">{item.question}</span>
                    <ChevronRight
                      className={`h-5 w-5 text-gray-400 transition-transform ${
                        expandedFaq === item.id ? 'rotate-90' : ''
                      }`}
                    />
                  </button>
                  {expandedFaq === item.id && (
                    <div className="px-4 pb-4 text-gray-600">{item.answer}</div>
                  )}
                </div>
              ))}
            </div>
          </Card>
        </div>

        {/* Resources & Contact */}
        <div className="space-y-6">
          <Card className="p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">Resources</h2>
            <div className="space-y-3">
              {resources.map((resource, i) => {
                const Icon = resource.icon;
                return (
                  <a
                    key={i}
                    href={resource.href}
                    className="flex items-center p-3 rounded-lg hover:bg-gray-50 transition-colors"
                  >
                    <div className="p-2 bg-blue-50 rounded-lg mr-3">
                      <Icon className="h-5 w-5 text-blue-600" />
                    </div>
                    <div>
                      <p className="font-medium text-gray-900">{resource.title}</p>
                      <p className="text-sm text-gray-500">{resource.description}</p>
                    </div>
                  </a>
                );
              })}
            </div>
          </Card>

          <Card className="p-6">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">Contact Support</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Subject</label>
                <input
                  type="text"
                  value={contactForm.subject}
                  onChange={(e) => setContactForm({ ...contactForm, subject: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md"
                  placeholder="Brief description"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Message</label>
                <textarea
                  value={contactForm.message}
                  onChange={(e) => setContactForm({ ...contactForm, message: e.target.value })}
                  rows={4}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md"
                  placeholder="Describe your issue..."
                />
              </div>
              <Button className="w-full">
                <Mail className="mr-2 h-4 w-4" />
                Send Message
              </Button>
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
};

export default Support;
