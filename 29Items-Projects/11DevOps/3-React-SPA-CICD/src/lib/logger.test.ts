import { logger, registerErrorTransport } from './logger';

describe('logger', () => {
  afterEach(() => registerErrorTransport(null));

  it('prefixes messages with the app and environment', () => {
    const spy = jest.spyOn(console, 'info').mockImplementation(() => undefined);

    logger.info('hello', { a: 1 });

    expect(spy).toHaveBeenCalledWith('[portal:test]', 'hello', { a: 1 });
  });

  it('logs all levels in non-production (threshold = debug)', () => {
    const debugSpy = jest.spyOn(console, 'debug').mockImplementation(() => undefined);
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => undefined);

    logger.debug('low level');
    logger.error('high level');

    expect(debugSpy).toHaveBeenCalledTimes(1);
    expect(errorSpy).toHaveBeenCalledTimes(1);
  });

  it('forwards warn/error — and only those — to the registered transport', () => {
    jest.spyOn(console, 'debug').mockImplementation(() => undefined);
    jest.spyOn(console, 'warn').mockImplementation(() => undefined);
    jest.spyOn(console, 'error').mockImplementation(() => undefined);

    const transport = jest.fn();
    registerErrorTransport(transport);

    logger.debug('not forwarded');
    logger.warn('forwarded warn', { code: 1 });
    logger.error('forwarded error');

    expect(transport).toHaveBeenCalledTimes(2);
    expect(transport).toHaveBeenNthCalledWith(1, 'warn', 'forwarded warn', { code: 1 });
    expect(transport).toHaveBeenNthCalledWith(2, 'error', 'forwarded error', undefined);
  });

  it('a throwing transport never breaks the caller', () => {
    jest.spyOn(console, 'error').mockImplementation(() => undefined);
    registerErrorTransport(() => {
      throw new Error('transport down');
    });

    expect(() => logger.error('still fine')).not.toThrow();
  });
});
