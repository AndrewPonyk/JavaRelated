import { describe, expect, it } from 'vitest';
import { handler } from '../../netlify/functions/reservations.js';

describe('netlify reservation function adapter', () => {
  it('rejects write requests without JSON content type before database access', async () => {
    const response = await handler({
      httpMethod: 'POST',
      headers: { 'content-type': 'text/plain' },
      queryStringParameters: {},
      body: 'not-json'
    });

    expect(response.statusCode).toBe(415);
    expect(JSON.parse(response.body).code).toBe('unsupported_media_type');
  });
});
