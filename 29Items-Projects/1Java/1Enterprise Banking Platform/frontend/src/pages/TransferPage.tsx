import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAccounts } from '../hooks/useAccounts';
import { useTransfer } from '../hooks/useTransactions';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorMessage from '../components/common/ErrorMessage';
import { formatCurrency } from '../utils/format';

const transferSchema = z.object({
  sourceAccountId: z.string().min(1, 'Please select a source account'),
  targetAccountId: z.string().min(1, 'Please select a target account'),
  amount: z.number().min(0.01, 'Amount must be greater than 0'),
  description: z.string().optional(),
}).refine((data) => data.sourceAccountId !== data.targetAccountId, {
  message: 'Source and target accounts must be different',
  path: ['targetAccountId'],
});

type TransferFormData = z.infer<typeof transferSchema>;

export default function TransferPage() {
  const { data: accounts, isLoading: accountsLoading } = useAccounts();
  const transfer = useTransfer();
  const [successMessage, setSuccessMessage] = useState('');

  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<TransferFormData>({
    resolver: zodResolver(transferSchema),
  });

  const sourceAccountId = watch('sourceAccountId');
  const sourceAccount = accounts?.find((a) => a.id === sourceAccountId);

  const onSubmit = async (data: TransferFormData) => {
    setSuccessMessage('');

    try {
      await transfer.mutateAsync({
        sourceAccountId: data.sourceAccountId,
        targetAccountId: data.targetAccountId,
        transactionType: 'INTERNAL_TRANSFER',
        amount: data.amount,
        currency: sourceAccount?.currency ?? 'USD',
        description: data.description,
      });

      setSuccessMessage('Transfer completed successfully!');
      reset();
    } catch (error) {
      // Error is handled by mutation
    }
  };

  if (accountsLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Transfer Funds</h1>
        <p className="text-gray-600 mt-1">
          Transfer money between your accounts
        </p>
      </div>

      <div className="card">
        {successMessage && (
          <div className="mb-6 rounded-lg bg-green-50 p-4">
            <p className="text-sm text-green-700">{successMessage}</p>
          </div>
        )}

        {transfer.error && (
          <div className="mb-6">
            <ErrorMessage message="Transfer failed. Please try again." />
          </div>
        )}

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
          {/* Source Account */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              From Account
            </label>
            <select
              {...register('sourceAccountId')}
              className="input"
            >
              <option value="">Select source account</option>
              {accounts
                ?.filter((a) => a.status === 'ACTIVE')
                .map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.accountType} - ****{account.accountNumber.slice(-4)} ({formatCurrency(account.balance, account.currency)})
                  </option>
                ))}
            </select>
            {errors.sourceAccountId && (
              <p className="mt-1 text-sm text-red-600">
                {errors.sourceAccountId.message}
              </p>
            )}
            {sourceAccount && (
              <p className="mt-1 text-sm text-gray-500">
                Available: {formatCurrency(sourceAccount.balance, sourceAccount.currency)}
              </p>
            )}
          </div>

          {/* Target Account */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              To Account
            </label>
            <select
              {...register('targetAccountId')}
              className="input"
            >
              <option value="">Select target account</option>
              {accounts
                ?.filter((a) => a.status === 'ACTIVE' && a.id !== sourceAccountId)
                .map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.accountType} - ****{account.accountNumber.slice(-4)}
                  </option>
                ))}
            </select>
            {errors.targetAccountId && (
              <p className="mt-1 text-sm text-red-600">
                {errors.targetAccountId.message}
              </p>
            )}
          </div>

          {/* Amount */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Amount
            </label>
            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500">
                $
              </span>
              <input
                type="number"
                step="0.01"
                {...register('amount', { valueAsNumber: true })}
                className="input pl-8"
                placeholder="0.00"
              />
            </div>
            {errors.amount && (
              <p className="mt-1 text-sm text-red-600">
                {errors.amount.message}
              </p>
            )}
          </div>

          {/* Description */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Description (Optional)
            </label>
            <input
              type="text"
              {...register('description')}
              className="input"
              placeholder="What's this transfer for?"
            />
          </div>

          {/* Submit */}
          <button
            type="submit"
            disabled={isSubmitting || transfer.isPending}
            className="w-full btn-primary py-3"
          >
            {isSubmitting || transfer.isPending ? 'Processing...' : 'Transfer Funds'}
          </button>
        </form>
      </div>
    </div>
  );
}
