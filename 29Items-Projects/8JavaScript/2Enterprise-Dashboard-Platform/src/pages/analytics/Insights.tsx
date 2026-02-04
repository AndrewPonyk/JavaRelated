import React, { useState } from 'react';
import { TrendingUp, TrendingDown, Lightbulb, Target, Users, DollarSign, AlertTriangle, CheckCircle } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { cn } from '@/utils/cn';

const mockInsights = [
  {
    id: '1',
    title: 'Revenue Growth Opportunity',
    description: 'Based on current trends, increasing marketing spend by 15% could yield 25% higher conversions.',
    type: 'opportunity',
    impact: 'high',
    metric: '+25%',
    category: 'Revenue',
  },
  {
    id: '2',
    title: 'User Churn Risk Detected',
    description: '12% of premium users show decreased engagement. Consider retention campaign.',
    type: 'warning',
    impact: 'high',
    metric: '12%',
    category: 'Users',
  },
  {
    id: '3',
    title: 'Cost Optimization Available',
    description: 'Consolidating cloud resources could reduce infrastructure costs by $2,400/month.',
    type: 'savings',
    impact: 'medium',
    metric: '$2.4K',
    category: 'Costs',
  },
  {
    id: '4',
    title: 'Peak Performance Hours',
    description: 'User engagement peaks between 2-4 PM. Schedule important updates during this window.',
    type: 'insight',
    impact: 'low',
    metric: '2-4 PM',
    category: 'Performance',
  },
];

const kpis = [
  { label: 'Total Insights', value: '24', change: '+8', trend: 'up', icon: Lightbulb },
  { label: 'Opportunities', value: '12', change: '+3', trend: 'up', icon: Target },
  { label: 'Warnings', value: '5', change: '-2', trend: 'down', icon: AlertTriangle },
  { label: 'Implemented', value: '7', change: '+4', trend: 'up', icon: CheckCircle },
];

export const Insights: React.FC = () => {
  const [selectedCategory, setSelectedCategory] = useState('All');
  const categories = ['All', 'Revenue', 'Users', 'Costs', 'Performance'];

  const getTypeIcon = (type: string) => {
    switch (type) {
      case 'opportunity': return <TrendingUp className="h-5 w-5 text-green-500" />;
      case 'warning': return <AlertTriangle className="h-5 w-5 text-yellow-500" />;
      case 'savings': return <DollarSign className="h-5 w-5 text-blue-500" />;
      default: return <Lightbulb className="h-5 w-5 text-purple-500" />;
    }
  };

  const getImpactBadge = (impact: string) => {
    const styles = {
      high: 'bg-red-100 text-red-800',
      medium: 'bg-yellow-100 text-yellow-800',
      low: 'bg-green-100 text-green-800',
    };
    return <Badge className={styles[impact as keyof typeof styles]}>{impact} impact</Badge>;
  };

  const filteredInsights = selectedCategory === 'All'
    ? mockInsights
    : mockInsights.filter(i => i.category === selectedCategory);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Insights</h1>
          <p className="text-gray-600">AI-powered recommendations and analytics</p>
        </div>
        <Button>
          <Lightbulb className="mr-2 h-4 w-4" />
          Generate Insights
        </Button>
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {kpis.map((kpi, i) => (
          <Card key={i} className="p-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-500">{kpi.label}</p>
                <p className="text-2xl font-bold text-gray-900">{kpi.value}</p>
                <div className="flex items-center mt-1">
                  {kpi.trend === 'up' ? (
                    <TrendingUp className="h-4 w-4 text-green-500 mr-1" />
                  ) : (
                    <TrendingDown className="h-4 w-4 text-red-500 mr-1" />
                  )}
                  <span className={kpi.trend === 'up' ? 'text-green-600' : 'text-red-600'}>
                    {kpi.change}
                  </span>
                </div>
              </div>
              <kpi.icon className="h-8 w-8 text-gray-400" />
            </div>
          </Card>
        ))}
      </div>

      {/* Filters */}
      <div className="flex gap-2">
        {categories.map((cat) => (
          <button
            key={cat}
            onClick={() => setSelectedCategory(cat)}
            className={cn(
              'px-4 py-2 rounded-lg text-sm font-medium transition-colors',
              selectedCategory === cat
                ? 'bg-blue-600 text-white'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            )}
          >
            {cat}
          </button>
        ))}
      </div>

      {/* Insights List */}
      <div className="grid gap-4">
        {filteredInsights.map((insight) => (
          <Card key={insight.id} className="p-6">
            <div className="flex items-start justify-between">
              <div className="flex items-start space-x-4">
                <div className="p-2 bg-gray-100 rounded-lg">
                  {getTypeIcon(insight.type)}
                </div>
                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <h3 className="font-semibold text-gray-900">{insight.title}</h3>
                    {getImpactBadge(insight.impact)}
                  </div>
                  <p className="text-gray-600 mb-3">{insight.description}</p>
                  <div className="flex items-center gap-4">
                    <Badge variant="secondary">{insight.category}</Badge>
                    <span className="text-sm text-gray-500">Key metric: <strong>{insight.metric}</strong></span>
                  </div>
                </div>
              </div>
              <div className="flex gap-2">
                <Button variant="outline" size="sm">Dismiss</Button>
                <Button size="sm">Take Action</Button>
              </div>
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
};

export default Insights;
