import React, { useState } from 'react';
import { DollarSign, TrendingUp, TrendingDown, PieChart, BarChart3, ArrowUpRight, ArrowDownRight } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { cn } from '@/utils/cn';

const financialMetrics = [
  { label: 'Total Revenue', value: '$124,500', change: '+12.5%', trend: 'up', icon: DollarSign },
  { label: 'Net Profit', value: '$45,200', change: '+8.2%', trend: 'up', icon: TrendingUp },
  { label: 'Expenses', value: '$79,300', change: '-3.1%', trend: 'down', icon: TrendingDown },
  { label: 'ROI', value: '24.5%', change: '+2.3%', trend: 'up', icon: PieChart },
];

const transactions = [
  { id: '1', description: 'Enterprise License Sale', amount: '+$12,500', type: 'income', date: '2024-01-15', category: 'Sales' },
  { id: '2', description: 'Cloud Infrastructure', amount: '-$3,200', type: 'expense', date: '2024-01-14', category: 'Operations' },
  { id: '3', description: 'Consulting Services', amount: '+$8,000', type: 'income', date: '2024-01-13', category: 'Services' },
  { id: '4', description: 'Marketing Campaign', amount: '-$5,500', type: 'expense', date: '2024-01-12', category: 'Marketing' },
  { id: '5', description: 'Product Subscription', amount: '+$2,400', type: 'income', date: '2024-01-11', category: 'Recurring' },
];

const budgetItems = [
  { category: 'Marketing', allocated: 50000, spent: 35000 },
  { category: 'Development', allocated: 80000, spent: 62000 },
  { category: 'Operations', allocated: 40000, spent: 38000 },
  { category: 'Sales', allocated: 30000, spent: 22000 },
];

export const Financial: React.FC = () => {
  const [period, setPeriod] = useState<'week' | 'month' | 'quarter' | 'year'>('month');

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Financial Analytics</h1>
          <p className="text-gray-600">Track revenue, expenses, and financial performance</p>
        </div>
        <div className="flex gap-2">
          {(['week', 'month', 'quarter', 'year'] as const).map((p) => (
            <button
              key={p}
              onClick={() => setPeriod(p)}
              className={cn(
                'px-3 py-1 rounded-md text-sm capitalize',
                period === p ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              )}
            >
              {p}
            </button>
          ))}
        </div>
      </div>

      {/* Metrics */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {financialMetrics.map((metric, i) => (
          <Card key={i} className="p-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-500">{metric.label}</p>
                <p className="text-2xl font-bold text-gray-900">{metric.value}</p>
                <div className="flex items-center mt-1">
                  {metric.trend === 'up' ? (
                    <ArrowUpRight className="h-4 w-4 text-green-500" />
                  ) : (
                    <ArrowDownRight className="h-4 w-4 text-red-500" />
                  )}
                  <span className={metric.trend === 'up' ? 'text-green-600' : 'text-red-600'}>
                    {metric.change}
                  </span>
                </div>
              </div>
              <div className="p-3 bg-blue-50 rounded-lg">
                <metric.icon className="h-6 w-6 text-blue-600" />
              </div>
            </div>
          </Card>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent Transactions */}
        <Card className="p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-gray-900">Recent Transactions</h2>
            <Button variant="outline" size="sm">View All</Button>
          </div>
          <div className="space-y-3">
            {transactions.map((tx) => (
              <div key={tx.id} className="flex items-center justify-between py-2 border-b last:border-0">
                <div>
                  <p className="font-medium text-gray-900">{tx.description}</p>
                  <div className="flex items-center gap-2 mt-1">
                    <Badge variant="secondary" className="text-xs">{tx.category}</Badge>
                    <span className="text-xs text-gray-500">{tx.date}</span>
                  </div>
                </div>
                <span className={cn(
                  'font-semibold',
                  tx.type === 'income' ? 'text-green-600' : 'text-red-600'
                )}>
                  {tx.amount}
                </span>
              </div>
            ))}
          </div>
        </Card>

        {/* Budget Overview */}
        <Card className="p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-gray-900">Budget Overview</h2>
            <Button variant="outline" size="sm">Manage</Button>
          </div>
          <div className="space-y-4">
            {budgetItems.map((item, i) => {
              const percentage = Math.round((item.spent / item.allocated) * 100);
              return (
                <div key={i}>
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-sm font-medium text-gray-700">{item.category}</span>
                    <span className="text-sm text-gray-500">
                      ${item.spent.toLocaleString()} / ${item.allocated.toLocaleString()}
                    </span>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-2">
                    <div
                      className={cn(
                        'h-2 rounded-full',
                        percentage > 90 ? 'bg-red-500' : percentage > 70 ? 'bg-yellow-500' : 'bg-green-500'
                      )}
                      style={{ width: `${percentage}%` }}
                    />
                  </div>
                  <p className="text-xs text-gray-500 mt-1">{percentage}% used</p>
                </div>
              );
            })}
          </div>
        </Card>
      </div>

      {/* Chart Placeholder */}
      <Card className="p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-gray-900">Revenue vs Expenses</h2>
          <div className="flex gap-4">
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 bg-green-500 rounded-full" />
              <span className="text-sm text-gray-600">Revenue</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-3 h-3 bg-red-500 rounded-full" />
              <span className="text-sm text-gray-600">Expenses</span>
            </div>
          </div>
        </div>
        <div className="h-64 flex items-center justify-center bg-gray-50 rounded-lg">
          <div className="text-center text-gray-500">
            <BarChart3 className="h-12 w-12 mx-auto mb-2" />
            <p>Chart visualization</p>
            <p className="text-sm">Integrate with D3.js or Recharts</p>
          </div>
        </div>
      </Card>
    </div>
  );
};

export default Financial;
