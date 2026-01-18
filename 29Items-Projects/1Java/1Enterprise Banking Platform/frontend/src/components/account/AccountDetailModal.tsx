import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { XMarkIcon } from '@heroicons/react/24/outline';
import { Account } from '../../types/account';
import { formatCurrency, formatDate } from '../../utils/format';
import {
  useDeposit,
  useWithdraw,
  useFreezeAccount,
  useUnfreezeAccount,
  useCloseAccount,
} from '../../hooks/useAccounts';
import LoadingSpinner from '../common/LoadingSpinner';

interface AccountDetailModalProps {
  account: Account;
  onClose: () => void;
}

const amountSchema = z.object({
  amount: z.number().min(0.01, 'Amount must be greater than 0'),
  reference: z.string().optional(),
});

type AmountFormData = z.infer<typeof amountSchema>;

export default function AccountDetailModal({ account, onClose }: AccountDetailModalProps) {
  const [activeTab, setActiveTab] = useState<'details' | 'deposit' | 'withdraw'>('details');
  const [successMessage, setSuccessMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');

  const deposit = useDeposit();
  const withdraw = useWithdraw();
  const freezeAccount = useFreezeAccount();
  const unfreezeAccount = useUnfreezeAccount();
  const closeAccount = useCloseAccount();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<AmountFormData>({
    resolver: zodResolver(amountSchema),
  });

  const showMessage = (message: string, isError = false) => {
    if (isError) {
      setErrorMessage(message);
      setSuccessMessage('');
    } else {
      setSuccessMessage(message);
      setErrorMessage('');
    }
    setTimeout(() => {
      setSuccessMessage('');
      setErrorMessage('');
    }, 3000);
  };

  const handleDeposit = async (data: AmountFormData) => {
    try {
      await deposit.mutateAsync({
        accountId: account.id,
        request: {
          amount: data.amount,
          currency: account.currency,
          reference: data.reference,
        },
      });
      showMessage('Deposit successful!');
      reset();
      setActiveTab('details');
    } catch (err) {
      showMessage('Deposit failed. Please try again.', true);
    }
  };

  const handleWithdraw = async (data: AmountFormData) => {
    try {
      await withdraw.mutateAsync({
        accountId: account.id,
        request: {
          amount: data.amount,
          currency: account.currency,
          reference: data.reference,
        },
      });
      showMessage('Withdrawal successful!');
      reset();
      setActiveTab('details');
    } catch (err) {
      showMessage('Withdrawal failed. Insufficient funds or account frozen.', true);
    }
  };

  const handleFreeze = async () => {
    try {
      await freezeAccount.mutateAsync(account.id);
      showMessage('Account frozen successfully');
    } catch (err) {
      showMessage('Failed to freeze account', true);
    }
  };

  const handleUnfreeze = async () => {
    try {
      await unfreezeAccount.mutateAsync(account.id);
      showMessage('Account unfrozen successfully');
    } catch (err) {
      showMessage('Failed to unfreeze account', true);
    }
  };

  const handleClose = async () => {
    if (confirm('Are you sure you want to close this account? This action cannot be undone.')) {
      try {
        await closeAccount.mutateAsync(account.id);
        showMessage('Account closed successfully');
        onClose();
      } catch (err) {
        showMessage('Failed to close account. Ensure balance is zero.', true);
      }
    }
  };

  const statusColors = {
    ACTIVE: 'bg-green-100 text-green-800',
    FROZEN: 'bg-blue-100 text-blue-800',
    PENDING_ACTIVATION: 'bg-yellow-100 text-yellow-800',
    DORMANT: 'bg-gray-100 text-gray-800',
    CLOSED: 'bg-red-100 text-red-800',
  };

  const isAccountActive = account.status === 'ACTIVE';
  const isAccountFrozen = account.status === 'FROZEN';
  const canPerformActions = isAccountActive || isAccountFrozen;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="card w-full max-w-lg max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex justify-between items-start mb-6">
          <div>
            <h2 className="text-xl font-semibold text-gray-900">Account Details</h2>
            <p className="text-sm text-gray-500 font-mono mt-1">
              {account.accountNumber}
            </p>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-100"
          >
            <XMarkIcon className="w-5 h-5" />
          </button>
        </div>

        {/* Messages */}
        {successMessage && (
          <div className="mb-4 rounded-lg bg-green-50 p-4">
            <p className="text-sm text-green-700">{successMessage}</p>
          </div>
        )}
        {errorMessage && (
          <div className="mb-4 rounded-lg bg-red-50 p-4">
            <p className="text-sm text-red-700">{errorMessage}</p>
          </div>
        )}

        {/* Tabs */}
        <div className="flex space-x-1 mb-6 border-b border-gray-200">
          <button
            onClick={() => setActiveTab('details')}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
              activeTab === 'details'
                ? 'border-primary-600 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            Details
          </button>
          <button
            onClick={() => setActiveTab('deposit')}
            disabled={!isAccountActive}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
              activeTab === 'deposit'
                ? 'border-primary-600 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            } ${!isAccountActive ? 'opacity-50 cursor-not-allowed' : ''}`}
          >
            Deposit
          </button>
          <button
            onClick={() => setActiveTab('withdraw')}
            disabled={!isAccountActive}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
              activeTab === 'withdraw'
                ? 'border-primary-600 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            } ${!isAccountActive ? 'opacity-50 cursor-not-allowed' : ''}`}
          >
            Withdraw
          </button>
        </div>

        {/* Tab Content */}
        {activeTab === 'details' && (
          <div className="space-y-6">
            {/* Balance Card */}
            <div className="bg-gradient-to-r from-primary-600 to-primary-700 rounded-xl p-6 text-white">
              <p className="text-sm opacity-80">Available Balance</p>
              <p className="text-3xl font-bold mt-2">
                {formatCurrency(account.balance, account.currency)}
              </p>
              <div className="mt-4 flex items-center justify-between">
                <span className={`badge ${statusColors[account.status]} text-xs`}>
                  {account.status.replace('_', ' ')}
                </span>
                <span className="text-sm opacity-80">{account.accountType.replace('_', ' ')}</span>
              </div>
            </div>

            {/* Account Info */}
            <div className="grid grid-cols-2 gap-4 text-sm">
              <div>
                <p className="text-gray-500">Account Holder</p>
                <p className="font-medium text-gray-900">{account.ownerName}</p>
              </div>
              <div>
                <p className="text-gray-500">Email</p>
                <p className="font-medium text-gray-900">{account.ownerEmail}</p>
              </div>
              <div>
                <p className="text-gray-500">Currency</p>
                <p className="font-medium text-gray-900">{account.currency}</p>
              </div>
              <div>
                <p className="text-gray-500">Opened On</p>
                <p className="font-medium text-gray-900">
                  {formatDate(account.createdAt, { year: 'numeric', month: 'short', day: 'numeric' })}
                </p>
              </div>
            </div>

            {/* Action Buttons */}
            {canPerformActions && (
              <div className="flex flex-wrap gap-2 pt-4 border-t border-gray-200">
                {isAccountActive && (
                  <button
                    onClick={handleFreeze}
                    disabled={freezeAccount.isPending}
                    className="btn-secondary text-sm"
                  >
                    {freezeAccount.isPending ? 'Freezing...' : 'Freeze Account'}
                  </button>
                )}
                {isAccountFrozen && (
                  <button
                    onClick={handleUnfreeze}
                    disabled={unfreezeAccount.isPending}
                    className="btn-secondary text-sm"
                  >
                    {unfreezeAccount.isPending ? 'Unfreezing...' : 'Unfreeze Account'}
                  </button>
                )}
                <button
                  onClick={handleClose}
                  disabled={closeAccount.isPending || account.balance !== 0}
                  className="btn-danger text-sm"
                  title={account.balance !== 0 ? 'Balance must be zero to close' : ''}
                >
                  {closeAccount.isPending ? 'Closing...' : 'Close Account'}
                </button>
              </div>
            )}
          </div>
        )}

        {activeTab === 'deposit' && (
          <form onSubmit={handleSubmit(handleDeposit)} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Amount
              </label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500">$</span>
                <input
                  {...register('amount', { valueAsNumber: true })}
                  type="number"
                  step="0.01"
                  className="input pl-8"
                  placeholder="0.00"
                />
              </div>
              {errors.amount && (
                <p className="mt-1 text-sm text-red-600">{errors.amount.message}</p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Reference (Optional)
              </label>
              <input
                {...register('reference')}
                className="input"
                placeholder="e.g., Paycheck, Transfer from savings"
              />
            </div>

            <button
              type="submit"
              disabled={deposit.isPending}
              className="w-full btn-primary py-3"
            >
              {deposit.isPending ? (
                <LoadingSpinner size="sm" className="mx-auto" />
              ) : (
                'Deposit Funds'
              )}
            </button>
          </form>
        )}

        {activeTab === 'withdraw' && (
          <form onSubmit={handleSubmit(handleWithdraw)} className="space-y-4">
            <div className="bg-gray-50 rounded-lg p-4 mb-4">
              <p className="text-sm text-gray-500">Available Balance</p>
              <p className="text-2xl font-bold text-gray-900">
                {formatCurrency(account.balance, account.currency)}
              </p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Amount
              </label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500">$</span>
                <input
                  {...register('amount', { valueAsNumber: true })}
                  type="number"
                  step="0.01"
                  max={account.balance}
                  className="input pl-8"
                  placeholder="0.00"
                />
              </div>
              {errors.amount && (
                <p className="mt-1 text-sm text-red-600">{errors.amount.message}</p>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Reference (Optional)
              </label>
              <input
                {...register('reference')}
                className="input"
                placeholder="e.g., ATM withdrawal, Bill payment"
              />
            </div>

            <button
              type="submit"
              disabled={withdraw.isPending}
              className="w-full btn-primary py-3"
            >
              {withdraw.isPending ? (
                <LoadingSpinner size="sm" className="mx-auto" />
              ) : (
                'Withdraw Funds'
              )}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
