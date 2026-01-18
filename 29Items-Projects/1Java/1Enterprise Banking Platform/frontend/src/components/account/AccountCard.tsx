import { Account } from '../../types/account';
import { formatCurrency, formatAccountNumber } from '../../utils/format';

interface AccountCardProps {
  account: Account;
  onClick?: () => void;
}

export default function AccountCard({ account, onClick }: AccountCardProps) {
  const statusColors = {
    ACTIVE: 'bg-green-100 text-green-800',
    FROZEN: 'bg-blue-100 text-blue-800',
    PENDING_ACTIVATION: 'bg-yellow-100 text-yellow-800',
    DORMANT: 'bg-gray-100 text-gray-800',
    CLOSED: 'bg-red-100 text-red-800',
  };

  const typeLabels = {
    CHECKING: 'Checking',
    SAVINGS: 'Savings',
    BUSINESS: 'Business',
    MONEY_MARKET: 'Money Market',
    CERTIFICATE_OF_DEPOSIT: 'CD',
  };

  return (
    <div
      className="card cursor-pointer hover:shadow-md transition-shadow"
      onClick={onClick}
    >
      <div className="flex justify-between items-start">
        <div>
          <p className="text-sm text-gray-500">
            {typeLabels[account.accountType]}
          </p>
          <p className="text-lg font-mono text-gray-700 mt-1">
            {formatAccountNumber(account.accountNumber)}
          </p>
        </div>
        <span
          className={`badge ${statusColors[account.status]}`}
        >
          {account.status.replace('_', ' ')}
        </span>
      </div>

      <div className="mt-4">
        <p className="text-2xl font-semibold text-gray-900">
          {formatCurrency(account.balance, account.currency)}
        </p>
        <p className="text-sm text-gray-500 mt-1">
          Available balance
        </p>
      </div>

      <div className="mt-4 pt-4 border-t border-gray-100">
        <p className="text-sm text-gray-600">{account.ownerName}</p>
      </div>
    </div>
  );
}
