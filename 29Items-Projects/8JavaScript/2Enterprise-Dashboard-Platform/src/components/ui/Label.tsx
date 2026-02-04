import React from 'react';
import { cn } from '@/utils/cn';

export interface LabelProps extends React.LabelHTMLAttributes<HTMLLabelElement> {}

export const Label = React.forwardRef<HTMLLabelElement, LabelProps>(
  ({ className, ...props }, ref) => {
    return (
      <label
        ref={ref}
        className={cn(
          'text-sm font-medium text-gray-700 dark:text-gray-300',
          className
        )}
        {...props}
      />
    );
  }
);

Label.displayName = 'Label';

export default Label;
