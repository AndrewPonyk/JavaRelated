import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Utility function to merge Tailwind CSS classes with clsx
 * This function combines clsx for conditional classes and tailwind-merge
 * to handle Tailwind class conflicts and duplications
 *
 * @param inputs - Class values to be processed
 * @returns Merged and optimized class string
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

/**
 * Alternative utility for more complex class merging scenarios
 * Useful when you need more control over the merging process
 */
export function classNames(
  ...classes: (string | undefined | null | boolean | { [key: string]: boolean })[]
): string {
  return clsx(...classes);
}

/**
 * Utility to create conditional classes based on variants
 * Useful for component variants and theme switching
 */
export function createVariantClasses<T extends Record<string, any>>(
  base: string,
  variants: T,
  defaultVariant?: keyof T
): (variant?: keyof T) => string {
  return (variant = defaultVariant) => {
    const variantClasses = variant ? variants[variant] : '';
    return cn(base, variantClasses);
  };
}

/**
 * Utility to merge multiple class objects
 * Useful for theme objects and style configurations
 */
export function mergeClassObjects<T extends Record<string, string>>(
  ...objects: (T | undefined)[]
): T {
  return objects.reduce((acc, obj) => {
    if (!obj) return acc;

    const result = { ...acc };
    Object.keys(obj).forEach(key => {
      result[key as keyof T] = cn(acc[key as keyof T], obj[key as keyof T]) as T[keyof T];
    });

    return result;
  }, {} as T);
}

/**
 * Utility to create responsive classes
 * Generates classes for different breakpoints
 */
export function createResponsiveClasses(classes: {
  base?: string;
  sm?: string;
  md?: string;
  lg?: string;
  xl?: string;
  '2xl'?: string;
}): string {
  return cn(
    classes.base,
    classes.sm && `sm:${classes.sm}`,
    classes.md && `md:${classes.md}`,
    classes.lg && `lg:${classes.lg}`,
    classes.xl && `xl:${classes.xl}`,
    classes['2xl'] && `2xl:${classes['2xl']}`
  );
}

/**
 * Utility for creating focus-visible classes
 * Standardizes focus states across components
 */
export function focusClasses(variant: 'default' | 'ring' | 'outline' = 'default'): string {
  const variants = {
    default: 'focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2',
    ring: 'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2',
    outline: 'focus:outline-2 focus:outline-blue-500 focus:outline-offset-2'
  };

  return variants[variant];
}

/**
 * Utility for creating transition classes
 * Standardizes animation transitions
 */
export function transitionClasses(
  type: 'default' | 'colors' | 'transform' | 'all' = 'default',
  duration: 'fast' | 'normal' | 'slow' = 'normal'
): string {
  const durations = {
    fast: 'duration-150',
    normal: 'duration-200',
    slow: 'duration-300'
  };

  const transitions = {
    default: 'transition-all ease-in-out',
    colors: 'transition-colors ease-in-out',
    transform: 'transition-transform ease-in-out',
    all: 'transition-all ease-in-out'
  };

  return cn(transitions[type], durations[duration]);
}