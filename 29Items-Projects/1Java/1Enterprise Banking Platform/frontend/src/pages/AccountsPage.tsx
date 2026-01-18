import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAccounts, useCreateAccount } from '../hooks/useAccounts';
import AccountCard from '../components/account/AccountCard';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorMessage from '../components/common/ErrorMessage';
import AccountDetailModal from '../components/account/AccountDetailModal';
import type { Account, AccountType } from '../types/account';

const createAccountSchema = z.object({
  ownerName: z.string().min(2, 'Name must be at least 2 characters'),
  ownerEmail: z.string().email('Please enter a valid email'),
  accountType: z.enum(['CHECKING', 'SAVINGS', 'BUSINESS', 'MONEY_MARKET', 'CERTIFICATE_OF_DEPOSIT']),
  currency: z.string().min(3, 'Currency is required'),
  initialDeposit: z.number().min(0, 'Initial deposit must be positive').optional(),
});

type CreateAccountFormData = z.infer<typeof createAccountSchema>;

export default function AccountsPage() {
  const { data: accounts, isLoading, error, refetch } = useAccounts();
  const createAccount = useCreateAccount();
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [selectedAccount, setSelectedAccount] = useState<Account | null>(null);
  const [filterType, setFilterType] = useState<string>('All');
  const [successMessage, setSuccessMessage] = useState('');

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CreateAccountFormData>({
    resolver: zodResolver(createAccountSchema),
    defaultValues: {
      currency: 'USD',
      accountType: 'CHECKING',
      initialDeposit: 0,
    },
  });

  const onSubmit = async (data: CreateAccountFormData) => {
    try {
      await createAccount.mutateAsync({
        ownerName: data.ownerName,
        ownerEmail: data.ownerEmail,
        accountType: data.accountType as AccountType,
        currency: data.currency,
        initialDeposit: data.initialDeposit,
      });
      setSuccessMessage('Account created successfully!');
      setShowCreateModal(false);
      reset();
      setTimeout(() => setSuccessMessage(''), 3000);
    } catch (err) {
      // Error handled by mutation
    }
  };

  const filteredAccounts = accounts?.filter((account) => {
    if (filterType === 'All') return true;
    return account.accountType === filterType.toUpperCase();
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  if (error) {
    return (
      <ErrorMessage
        title="Failed to load accounts"
        message="Could not load your accounts. Please try again."
        onRetry={refetch}
      />
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Accounts</h1>
          <p className="text-gray-600 mt-1">
            Manage your bank accounts
          </p>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          className="btn-primary"
        >
          + New Account
        </button>
      </div>

      {successMessage && (
        <div className="rounded-lg bg-green-50 p-4">
          <p className="text-sm text-green-700">{successMessage}</p>
        </div>
      )}

      {/* Account Type Filter */}
      <div className="flex space-x-2">
        {['All', 'Checking', 'Savings', 'Business'].map((type) => (
          <button
            key={type}
            onClick={() => setFilterType(type)}
            className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors ${
              filterType === type
                ? 'bg-primary-600 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
          >
            {type}
          </button>
        ))}
      </div>

      {/* Accounts Grid */}
      {filteredAccounts && filteredAccounts.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {filteredAccounts.map((account) => (
            <AccountCard
              key={account.id}
              account={account}
              onClick={() => setSelectedAccount(account)}
            />
          ))}
        </div>
      ) : (
        <div className="card text-center py-12">
          <p className="text-gray-500">No accounts found</p>
          <button
            onClick={() => setShowCreateModal(true)}
            className="btn-primary mt-4"
          >
            Open your first account
          </button>
        </div>
      )}

      {/* Create Account Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="card w-full max-w-md max-h-[90vh] overflow-y-auto">
            <h2 className="text-xl font-semibold mb-4">Create New Account</h2>

            {createAccount.error && (
              <div className="mb-4 rounded-lg bg-red-50 p-4">
                <p className="text-sm text-red-700">Failed to create account. Please try again.</p>
              </div>
            )}

            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Account Holder Name
                </label>
                <input
                  {...register('ownerName')}
                  className="input"
                  placeholder="John Doe"
                />
                {errors.ownerName && (
                  <p className="mt-1 text-sm text-red-600">{errors.ownerName.message}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Email Address
                </label>
                <input
                  {...register('ownerEmail')}
                  type="email"
                  className="input"
                  placeholder="john@example.com"
                />
                {errors.ownerEmail && (
                  <p className="mt-1 text-sm text-red-600">{errors.ownerEmail.message}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Account Type
                </label>
                <select {...register('accountType')} className="input">
                  <option value="CHECKING">Checking Account</option>
                  <option value="SAVINGS">Savings Account</option>
                  <option value="BUSINESS">Business Account</option>
                  <option value="MONEY_MARKET">Money Market</option>
                  <option value="CERTIFICATE_OF_DEPOSIT">Certificate of Deposit</option>
                </select>
                {errors.accountType && (
                  <p className="mt-1 text-sm text-red-600">{errors.accountType.message}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Currency
                </label>
                <select {...register('currency')} className="input">
                  <option value="USD">USD - US Dollar</option>
                  <option value="EUR">EUR - Euro</option>
                  <option value="GBP">GBP - British Pound</option>
                  <option value="CAD">CAD - Canadian Dollar</option>
                </select>
                {errors.currency && (
                  <p className="mt-1 text-sm text-red-600">{errors.currency.message}</p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Initial Deposit (Optional)
                </label>
                <div className="relative">
                  <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500">$</span>
                  <input
                    {...register('initialDeposit', { valueAsNumber: true })}
                    type="number"
                    step="0.01"
                    className="input pl-8"
                    placeholder="0.00"
                  />
                </div>
                {errors.initialDeposit && (
                  <p className="mt-1 text-sm text-red-600">{errors.initialDeposit.message}</p>
                )}
              </div>

              <div className="flex justify-end space-x-3 pt-4">
                <button
                  type="button"
                  onClick={() => {
                    setShowCreateModal(false);
                    reset();
                  }}
                  className="btn-secondary"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting || createAccount.isPending}
                  className="btn-primary"
                >
                  {isSubmitting || createAccount.isPending ? 'Creating...' : 'Create Account'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Account Detail Modal */}
      {selectedAccount && (
        <AccountDetailModal
          account={selectedAccount}
          onClose={() => setSelectedAccount(null)}
        />
      )}
    </div>
  );
}
