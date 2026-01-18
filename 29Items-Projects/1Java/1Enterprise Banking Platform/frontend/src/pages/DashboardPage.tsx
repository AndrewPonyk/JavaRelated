import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useAccounts } from '../hooks/useAccounts';
import { useAccountTransactions } from '../hooks/useTransactions';
import { useMyLoans, useMyLoanApplications } from '../hooks/useLoans';
import AccountCard from '../components/account/AccountCard';
import TransactionList from '../components/transaction/TransactionList';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorMessage from '../components/common/ErrorMessage';
import { formatCurrency } from '../utils/format';
import {
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  CreditCardIcon,
  BanknotesIcon,
  ExclamationTriangleIcon,
} from '@heroicons/react/24/outline';

export default function DashboardPage() {
  const { data: accounts, isLoading: accountsLoading, error: accountsError, refetch: refetchAccounts } = useAccounts();
  const { data: loans } = useMyLoans();
  const { data: loanApplications } = useMyLoanApplications();

  const primaryAccountId = accounts?.[0]?.id || '';
  const { data: transactions } = useAccountTransactions(primaryAccountId, 0, 5);

  const totalBalance = useMemo(() => {
    return accounts?.reduce((sum, acc) => sum + acc.balance, 0) ?? 0;
  }, [accounts]);

  const activeAccountsCount = useMemo(() => {
    return accounts?.filter((a) => a.status === 'ACTIVE').length ?? 0;
  }, [accounts]);

  const monthlyStats = useMemo(() => {
    if (!transactions) return { income: 0, expenses: 0 };

    const now = new Date();
    const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);

    let income = 0;
    let expenses = 0;

    transactions.forEach((tx) => {
      const txDate = new Date(tx.initiatedAt);
      if (txDate >= startOfMonth && tx.status === 'COMPLETED') {
        if (tx.targetAccountId === primaryAccountId) {
          income += tx.amount;
        } else if (tx.sourceAccountId === primaryAccountId) {
          expenses += tx.amount;
        }
      }
    });

    return { income, expenses };
  }, [transactions, primaryAccountId]);

  const totalOutstandingLoans = useMemo(() => {
    return loans?.reduce((sum, loan) => sum + loan.outstandingBalance, 0) ?? 0;
  }, [loans]);

  const pendingApplications = useMemo(() => {
    return loanApplications?.filter((app) =>
      ['SUBMITTED', 'UNDER_REVIEW', 'DOCUMENTS_REQUIRED'].includes(app.status)
    ).length ?? 0;
  }, [loanApplications]);

  if (accountsLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  if (accountsError) {
    return (
      <ErrorMessage
        title="Failed to load dashboard"
        message="Could not load your account information. Please try again."
        onRetry={refetchAccounts}
      />
    );
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <p className="text-gray-600 mt-1">
          Welcome back! Here's an overview of your finances.
        </p>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <div className="card">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-500">Total Balance</p>
              <p className="text-2xl font-bold text-gray-900 mt-1">
                {formatCurrency(totalBalance)}
              </p>
            </div>
            <div className="p-3 bg-primary-100 rounded-full">
              <CreditCardIcon className="w-6 h-6 text-primary-600" />
            </div>
          </div>
          <p className="text-xs text-gray-500 mt-2">
            Across {activeAccountsCount} active account{activeAccountsCount !== 1 ? 's' : ''}
          </p>
        </div>

        <div className="card">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-500">This Month Income</p>
              <p className="text-2xl font-bold text-green-600 mt-1">
                +{formatCurrency(monthlyStats.income)}
              </p>
            </div>
            <div className="p-3 bg-green-100 rounded-full">
              <ArrowTrendingUpIcon className="w-6 h-6 text-green-600" />
            </div>
          </div>
          <p className="text-xs text-gray-500 mt-2">
            Deposits and transfers received
          </p>
        </div>

        <div className="card">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-500">This Month Expenses</p>
              <p className="text-2xl font-bold text-red-600 mt-1">
                -{formatCurrency(monthlyStats.expenses)}
              </p>
            </div>
            <div className="p-3 bg-red-100 rounded-full">
              <ArrowTrendingDownIcon className="w-6 h-6 text-red-600" />
            </div>
          </div>
          <p className="text-xs text-gray-500 mt-2">
            Withdrawals and transfers sent
          </p>
        </div>

        <div className="card">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-500">Outstanding Loans</p>
              <p className="text-2xl font-bold text-gray-900 mt-1">
                {formatCurrency(totalOutstandingLoans)}
              </p>
            </div>
            <div className="p-3 bg-yellow-100 rounded-full">
              <BanknotesIcon className="w-6 h-6 text-yellow-600" />
            </div>
          </div>
          {pendingApplications > 0 && (
            <p className="text-xs text-yellow-600 mt-2 flex items-center">
              <ExclamationTriangleIcon className="w-4 h-4 mr-1" />
              {pendingApplications} pending application{pendingApplications !== 1 ? 's' : ''}
            </p>
          )}
        </div>
      </div>

      {/* Quick Actions */}
      <div className="card">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Quick Actions</h2>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Link
            to="/transfer"
            className="p-4 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors text-center"
          >
            <div className="w-10 h-10 bg-primary-100 rounded-full flex items-center justify-center mx-auto mb-2">
              <ArrowTrendingUpIcon className="w-5 h-5 text-primary-600" />
            </div>
            <p className="text-sm font-medium text-gray-900">Transfer</p>
          </Link>
          <Link
            to="/accounts"
            className="p-4 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors text-center"
          >
            <div className="w-10 h-10 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-2">
              <CreditCardIcon className="w-5 h-5 text-green-600" />
            </div>
            <p className="text-sm font-medium text-gray-900">New Account</p>
          </Link>
          <Link
            to="/loans"
            className="p-4 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors text-center"
          >
            <div className="w-10 h-10 bg-yellow-100 rounded-full flex items-center justify-center mx-auto mb-2">
              <BanknotesIcon className="w-5 h-5 text-yellow-600" />
            </div>
            <p className="text-sm font-medium text-gray-900">Apply for Loan</p>
          </Link>
          <Link
            to="/transactions"
            className="p-4 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors text-center"
          >
            <div className="w-10 h-10 bg-blue-100 rounded-full flex items-center justify-center mx-auto mb-2">
              <ArrowTrendingDownIcon className="w-5 h-5 text-blue-600" />
            </div>
            <p className="text-sm font-medium text-gray-900">View History</p>
          </Link>
        </div>
      </div>

      {/* Accounts and Transactions */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Accounts Grid */}
        <div>
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-lg font-semibold text-gray-900">Your Accounts</h2>
            <Link to="/accounts" className="text-sm text-primary-600 hover:text-primary-700">
              View all
            </Link>
          </div>

          {accounts && accounts.length > 0 ? (
            <div className="space-y-4">
              {accounts.slice(0, 3).map((account) => (
                <AccountCard key={account.id} account={account} />
              ))}
            </div>
          ) : (
            <div className="card text-center py-12">
              <p className="text-gray-500">No accounts found</p>
              <Link to="/accounts" className="btn-primary mt-4 inline-block">
                Open your first account
              </Link>
            </div>
          )}
        </div>

        {/* Recent Transactions */}
        <div>
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-lg font-semibold text-gray-900">Recent Transactions</h2>
            <Link to="/transactions" className="text-sm text-primary-600 hover:text-primary-700">
              View all
            </Link>
          </div>

          <div className="card">
            {transactions && transactions.length > 0 ? (
              <TransactionList
                transactions={transactions.slice(0, 5)}
                currentAccountId={primaryAccountId}
              />
            ) : (
              <div className="text-center py-8 text-gray-500">
                <p>No recent transactions</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Active Loans */}
      {loans && loans.length > 0 && (
        <div>
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-lg font-semibold text-gray-900">Active Loans</h2>
            <Link to="/loans" className="text-sm text-primary-600 hover:text-primary-700">
              View all
            </Link>
          </div>

          <div className="card">
            <div className="divide-y divide-gray-100">
              {loans.slice(0, 3).map((loan) => (
                <div key={loan.id} className="py-4 flex items-center justify-between">
                  <div>
                    <p className="font-medium text-gray-900">{loan.loanType.replace('_', ' ')}</p>
                    <p className="text-sm text-gray-500">
                      {loan.loanNumber} &middot; {loan.termMonths} months
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="font-semibold text-gray-900">
                      {formatCurrency(loan.outstandingBalance)}
                    </p>
                    <p className="text-sm text-gray-500">
                      {formatCurrency(loan.monthlyPayment)}/mo
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
