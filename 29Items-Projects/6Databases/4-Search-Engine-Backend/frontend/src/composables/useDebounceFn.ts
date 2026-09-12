import { onBeforeUnmount } from "vue";

/** Debounce a callback; pending call is cancelled on component unmount. */
export function useDebounceFn<Args extends unknown[]>(
  fn: (...args: Args) => void,
  delayMs = 150,
): (...args: Args) => void {
  let timer: ReturnType<typeof setTimeout> | undefined;

  onBeforeUnmount(() => clearTimeout(timer));

  return (...args: Args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delayMs);
  };
}
