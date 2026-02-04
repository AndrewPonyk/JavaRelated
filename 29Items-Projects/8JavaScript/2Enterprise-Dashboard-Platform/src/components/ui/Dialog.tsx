import React, { Fragment } from 'react';
import { X } from 'lucide-react';
import { cn } from '@/utils/cn';

interface DialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  children: React.ReactNode;
}

interface DialogContentProps {
  children: React.ReactNode;
  className?: string;
}

interface DialogHeaderProps {
  children: React.ReactNode;
  className?: string;
}

interface DialogTitleProps {
  children: React.ReactNode;
  className?: string;
}

interface DialogDescriptionProps {
  children: React.ReactNode;
  className?: string;
}

interface DialogFooterProps {
  children: React.ReactNode;
  className?: string;
}

export const Dialog: React.FC<DialogProps> = ({ open, onOpenChange, children }) => {
  if (!open) return null;

  return (
    <Fragment>
      {/* Backdrop */}
      <div
        className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm"
        onClick={() => onOpenChange(false)}
      />
      {/* Dialog */}
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div
          className="relative bg-white dark:bg-gray-800 rounded-lg shadow-xl max-h-[90vh] overflow-auto"
          onClick={(e) => e.stopPropagation()}
        >
          {children}
        </div>
      </div>
    </Fragment>
  );
};

export const DialogContent: React.FC<DialogContentProps> = ({ children, className }) => {
  return (
    <div className={cn('p-6 min-w-[400px] max-w-lg', className)}>
      {children}
    </div>
  );
};

export const DialogHeader: React.FC<DialogHeaderProps> = ({ children, className }) => {
  return (
    <div className={cn('mb-4', className)}>
      {children}
    </div>
  );
};

export const DialogTitle: React.FC<DialogTitleProps> = ({ children, className }) => {
  return (
    <h2 className={cn('text-lg font-semibold text-gray-900 dark:text-white', className)}>
      {children}
    </h2>
  );
};

export const DialogDescription: React.FC<DialogDescriptionProps> = ({ children, className }) => {
  return (
    <p className={cn('text-sm text-gray-500 dark:text-gray-400 mt-1', className)}>
      {children}
    </p>
  );
};

export const DialogFooter: React.FC<DialogFooterProps> = ({ children, className }) => {
  return (
    <div className={cn('mt-6 flex justify-end gap-3', className)}>
      {children}
    </div>
  );
};

export const DialogClose: React.FC<{ onClose: () => void; className?: string }> = ({ onClose, className }) => {
  return (
    <button
      onClick={onClose}
      className={cn(
        'absolute top-4 right-4 p-1 rounded-md text-gray-400 hover:text-gray-600 hover:bg-gray-100',
        'dark:hover:text-gray-300 dark:hover:bg-gray-700',
        className
      )}
    >
      <X className="h-4 w-4" />
    </button>
  );
};

export default Dialog;
