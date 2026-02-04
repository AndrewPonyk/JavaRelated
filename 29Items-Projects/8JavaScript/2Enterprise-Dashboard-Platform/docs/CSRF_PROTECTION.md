# CSRF Protection Implementation

This document describes the Cross-Site Request Forgery (CSRF) protection implementation in the Enterprise Dashboard Platform.

## Overview

CSRF protection prevents unauthorized commands from being transmitted from a user that the application trusts. Our implementation uses a double-submit cookie pattern with server-side token validation.

## Architecture

### Backend Components

1. **CSRF Middleware** (`backend/src/middleware/security/csrfMiddleware.ts`)
   - Token generation and validation
   - Double-submit cookie validation
   - Session-based token management
   - Redis-backed token storage

2. **CSRF Routes** (`/api/auth/csrf-token`)
   - `GET /api/auth/csrf-token` - Generate new token
   - `POST /api/auth/csrf-token/refresh` - Refresh existing token

3. **Protected Routes**
   - Auth routes: register, login, logout, password operations
   - Dashboard routes: create, update, delete, share operations
   - User management routes (state-changing operations)

### Frontend Components

1. **CSRF Service** (`src/services/security/csrfService.ts`)
   - Automatic token fetching and caching
   - Token refresh with retry logic
   - Axios interceptors for automatic token inclusion

2. **CSRF Provider** (`src/components/security/CSRFProvider.tsx`)
   - React context for CSRF state management
   - Automatic initialization on app startup
   - Error handling and fallbacks

3. **CSRF Hooks** (`src/hooks/useCSRF.ts`)
   - Easy integration with React components
   - Form submission utilities
   - API call helpers

## Configuration

### Environment Variables

```bash
# Enable/disable CSRF protection
ENABLE_CSRF=true
```

### Backend Configuration

The CSRF middleware is automatically configured with these settings:

- **Token Length**: 32 bytes (64 hex characters)
- **Token TTL**: 1 hour
- **Storage**: Redis with automatic cleanup
- **Header Name**: `X-CSRF-Token`
- **Cookie Name**: `csrf-token`

## Usage Examples

### Basic Form Submission

```tsx
import React from 'react';
import { useCSRFForm } from '@/hooks/useCSRF';

const CreateDashboard: React.FC = () => {
  const { submitWithCSRF, loading, error } = useCSRFForm();

  const handleSubmit = async (formData: { title: string; description: string }) => {
    try {
      const result = await submitWithCSRF(
        async (data) => {
          const response = await fetch('/api/dashboards', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify(data),
          });
          return response.json();
        },
        formData
      );

      console.log('Dashboard created:', result);
    } catch (err) {
      console.error('Failed to create dashboard:', err);
    }
  };

  return (
    <form onSubmit={(e) => {
      e.preventDefault();
      const formData = new FormData(e.target as HTMLFormElement);
      handleSubmit({
        title: formData.get('title') as string,
        description: formData.get('description') as string,
      });
    }}>
      <input name="title" placeholder="Dashboard Title" required />
      <textarea name="description" placeholder="Description" />
      <button type="submit" disabled={loading}>
        {loading ? 'Creating...' : 'Create Dashboard'}
      </button>
      {error && <div className="error">{error}</div>}
    </form>
  );
};
```

### Direct API Calls

```tsx
import { useCSRFApi } from '@/hooks/useCSRF';

const DeleteButton: React.FC<{ dashboardId: string }> = ({ dashboardId }) => {
  const { callWithCSRF } = useCSRFApi();

  const handleDelete = async () => {
    try {
      await callWithCSRF(async (token) => {
        const response = await fetch(`/api/dashboards/${dashboardId}`, {
          method: 'DELETE',
          headers: {
            'Content-Type': 'application/json',
            'X-CSRF-Token': token,
          },
          credentials: 'include',
        });

        if (!response.ok) {
          throw new Error('Failed to delete dashboard');
        }

        return response.json();
      });
    } catch (error) {
      console.error('Delete failed:', error);
    }
  };

  return <button onClick={handleDelete}>Delete Dashboard</button>;
};
```

### Manual Token Management

```tsx
import { useCSRF } from '@/hooks/useCSRF';

const ManualTokenExample: React.FC = () => {
  const { token, getToken, withCSRF } = useCSRF();

  const handleCustomSubmit = async () => {
    // Get fresh token
    const freshToken = await getToken();

    // Or add to existing data
    const formData = { title: 'New Dashboard' };
    const protectedData = withCSRF(formData);

    console.log('Protected data:', protectedData);
    // Output: { title: 'New Dashboard', _csrf: 'abc123...' }
  };

  return (
    <div>
      <p>Current token: {token?.substring(0, 10)}...</p>
      <button onClick={handleCustomSubmit}>Submit with CSRF</button>
    </div>
  );
};
```

## Automatic Integration

The CSRF service automatically integrates with your existing API calls:

1. **Axios Interceptors**: Automatically add CSRF tokens to POST, PUT, DELETE requests
2. **Error Handling**: Automatically retry failed requests with refreshed tokens
3. **Token Management**: Automatically refresh expired tokens

## Security Features

### Token Properties

- **Cryptographically Secure**: Generated using `crypto.randomBytes()`
- **One-Time Use**: Tokens are consumed after validation
- **Time-Limited**: 1-hour expiration with automatic refresh
- **Session-Bound**: Tokens are tied to user sessions

### Protection Mechanisms

- **Double-Submit Cookie**: Validates cookie and header tokens match
- **Timing Attack Protection**: Uses constant-time comparison
- **Rate Limiting**: Applied to token generation endpoints
- **Automatic Cleanup**: Expired tokens are automatically removed

### Development Mode

CSRF protection can be disabled in development:

```bash
ENABLE_CSRF=false
NODE_ENV=development
```

## Error Handling

### Common CSRF Errors

1. **Token Missing**: `CSRF token is required`
   - Solution: Ensure forms include CSRF token

2. **Token Invalid**: `Invalid or expired CSRF token`
   - Solution: Refresh token and retry request

3. **Token Mismatch**: `CSRF token mismatch`
   - Solution: Check cookie and header token consistency

### Error Responses

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "CSRF token is required",
    "statusCode": 400
  }
}
```

## Testing

### Unit Tests

```typescript
import { csrfService } from '@/services/security/csrfService';

describe('CSRF Service', () => {
  test('generates valid token', async () => {
    const token = await csrfService.getToken();
    expect(token).toBeDefined();
    expect(token.length).toBe(64); // 32 bytes as hex
  });

  test('handles token refresh', async () => {
    const token1 = await csrfService.getToken();
    const token2 = await csrfService.refreshToken();
    expect(token1).not.toBe(token2);
  });
});
```

### Integration Tests

```typescript
import request from 'supertest';
import app from '@/server';

describe('CSRF Protection', () => {
  test('blocks requests without CSRF token', async () => {
    const response = await request(app)
      .post('/api/dashboards')
      .send({ title: 'Test Dashboard' });

    expect(response.status).toBe(400);
    expect(response.body.error.code).toBe('VALIDATION_ERROR');
  });

  test('allows requests with valid CSRF token', async () => {
    // Get CSRF token
    const tokenResponse = await request(app)
      .get('/api/auth/csrf-token');

    const token = tokenResponse.body.data.token;

    // Use token in request
    const response = await request(app)
      .post('/api/dashboards')
      .set('X-CSRF-Token', token)
      .send({ title: 'Test Dashboard' });

    expect(response.status).toBe(201);
  });
});
```

## Monitoring

### Metrics to Monitor

- Token generation rate
- Token validation failures
- CSRF attack attempts
- Token cache hit/miss rates

### Logging

CSRF events are logged with appropriate security levels:

```typescript
// Successful validation
logger.debug('CSRF protection passed', { method, path, sessionId });

// Failed validation
logger.warn('CSRF validation failed', {
  method,
  path,
  sessionId,
  reason: 'Token not found or expired'
});

// Security events
logger.warn('Potential CSRF attack detected', {
  ip: req.ip,
  userAgent: req.get('user-agent'),
  endpoint: req.path
});
```

## Best Practices

1. **Always Use Hooks**: Use provided hooks for consistent CSRF handling
2. **Handle Errors Gracefully**: Implement proper error boundaries
3. **Monitor Logs**: Watch for unusual CSRF failure patterns
4. **Test Thoroughly**: Include CSRF tests in your test suite
5. **Update Dependencies**: Keep crypto libraries up to date

## Troubleshooting

### Common Issues

1. **Tokens Not Being Set**
   - Check that CSRFProvider wraps your app
   - Verify ENABLE_CSRF environment variable

2. **Tokens Expiring Too Quickly**
   - Check Redis connection and TTL settings
   - Monitor token refresh patterns

3. **Double-Submit Validation Failing**
   - Ensure cookies are being sent with requests
   - Check CORS and SameSite cookie settings

4. **Development Issues**
   - Set ENABLE_CSRF=false for local development if needed
   - Check that dev server is running on correct port

## Migration Guide

If you have existing forms without CSRF protection:

1. Wrap your app with CSRFProvider (already done)
2. Update form submission logic to use CSRF hooks
3. Test all state-changing operations
4. Monitor logs for CSRF failures
5. Gradually roll out to production

## Security Considerations

- CSRF protection is one layer of defense - maintain other security measures
- Regular security audits should include CSRF protection testing
- Monitor for bypass attempts and unusual patterns
- Keep token storage (Redis) secure and encrypted
- Use HTTPS in production to prevent token interception