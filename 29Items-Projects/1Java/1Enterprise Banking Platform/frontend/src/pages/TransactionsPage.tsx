import { useState } from 'react';
import { useAccounts } from '../hooks/useAccounts';
import { useAccountTransactions } from '../hooks/useTransactions';
import TransactionList from '../components/transaction/TransactionList';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorMessage from '../components/common/ErrorMessage';

export default function TransactionsPage() {
  const { data: accounts, isLoading: accountsLoading } = useAccounts();
  const [selectedAccountId, setSelectedAccountId] = useState<string>('');

  const {
    data: transactions,
    isLoading: transactionsLoading,
    error,
    refetch,
  } = useAccountTransactions(selectedAccountId);

  // Auto-select first account
  if (accounts?.length && !selectedAccountId) {
    setSelectedAccountId(accounts[0].id);
  }

  if (accountsLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Transactions</h1>
        <p className="text-gray-600 mt-1">
          View your transaction history
        </p>
      </div>

      {/* Account Selector */}
      <div className="card">
        <label className="block text-sm font-medium text-gray-700 mb-2">
          Select Account
        </label>
        <select
          value={selectedAccountId}
          onChange={(e) => setSelectedAccountId(e.target.value)}
          className="input max-w-md"
        >
          <option value="">Select an account</option>
          {accounts?.map((account) => (
            <option key={account.id} value={account.id}>
              {account.accountType} - ****{account.accountNumber.slice(-4)}
            </option>
          ))}
        </select>
      </div>

      {/* Transactions */}
      <div className="card">
        <div className="flex justify-between items-center mb-4">
          <h2 className="text-lg font-semibold text-gray-900">
            Recent Transactions
          </h2>
          <div className="flex space-x-2">
            <button className="btn-secondary text-sm">
              Export
            </button>
            <button className="btn-secondary text-sm">
              Filter
            </button>
          </div>
        </div>

        {!selectedAccountId ? (
          <p className="text-center py-8 text-gray-500">
            Select an account to view transactions
          </p>
        ) : transactionsLoading ? (
          <div className="flex items-center justify-center py-8">
            <LoadingSpinner />
          </div>
        ) : error ? (
          <ErrorMessage
            message="Failed to load transactions"
            onRetry={refetch}
          />
        ) : (
          <TransactionList
            transactions={transactions ?? []}
            currentAccountId={selectedAccountId}
          />
        )}
      </div>
    </div>
  );
}
