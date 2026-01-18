import { Transaction } from '../../types/transaction';
import { formatCurrency, formatDate } from '../../utils/format';
import {
  ArrowUpIcon,
  ArrowDownIcon,
  ClockIcon,
  CheckCircleIcon,
  XCircleIcon,
} from '@heroicons/react/24/outline';

interface TransactionListProps {
  transactions: Transaction[];
  currentAccountId?: string;
}

export default function TransactionList({
  transactions,
  currentAccountId,
}: TransactionListProps) {
  const getStatusIcon = (status: Transaction['status']) => {
    switch (status) {
      case 'COMPLETED':
        return <CheckCircleIcon className="w-5 h-5 text-green-500" />;
      case 'FAILED':
      case 'CANCELLED':
        return <XCircleIcon className="w-5 h-5 text-red-500" />;
      default:
        return <ClockIcon className="w-5 h-5 text-yellow-500" />;
    }
  };

  const isOutgoing = (tx: Transaction) =>
    currentAccountId && tx.sourceAccountId === currentAccountId;

  if (transactions.length === 0) {
    return (
      <div className="text-center py-8 text-gray-500">
        No transactions found
      </div>
    );
  }

  return (
    <div className="divide-y divide-gray-100">
      {transactions.map((tx) => (
        <div
          key={tx.id}
          className="py-4 flex items-center justify-between hover:bg-gray-50 px-2 rounded-lg"
        >
          <div className="flex items-center space-x-4">
            <div
              className={`p-2 rounded-full ${
                isOutgoing(tx) ? 'bg-red-100' : 'bg-green-100'
              }`}
            >
              {isOutgoing(tx) ? (
                <ArrowUpIcon className="w-4 h-4 text-red-600" />
              ) : (
                <ArrowDownIcon className="w-4 h-4 text-green-600" />
              )}
            </div>

            <div>
              <p className="text-sm font-medium text-gray-900">
                {tx.description || tx.transactionType.replace('_', ' ')}
              </p>
              <p className="text-xs text-gray-500">
                {formatDate(tx.initiatedAt)} &middot; {tx.referenceNumber}
              </p>
            </div>
          </div>

          <div className="flex items-center space-x-4">
            <div className="text-right">
              <p
                className={`text-sm font-semibold ${
                  isOutgoing(tx) ? 'text-red-600' : 'text-green-600'
                }`}
              >
                {isOutgoing(tx) ? '-' : '+'}
                {formatCurrency(tx.amount, tx.currency)}
              </p>
              <p className="text-xs text-gray-500">{tx.status}</p>
            </div>
            {getStatusIcon(tx.status)}
          </div>
        </div>
      ))}
    </div>
  );
}
