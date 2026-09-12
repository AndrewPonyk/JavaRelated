declare module 'jest-axe' {
  export const axe: (html: Element | Document | string) => Promise<unknown>;
  export const toHaveNoViolations: jest.ExpectExtendMap;
}

declare namespace jest {
  interface Matchers<R> {
    toHaveNoViolations(): R;
  }
}
