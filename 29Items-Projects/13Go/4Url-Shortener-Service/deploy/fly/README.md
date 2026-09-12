# Fly.io Deployment Notes

Set secrets before deploying:

```bash
fly secrets set DATABASE_URL="postgres://..."
fly secrets set REDIS_ADDR="..."
fly secrets set REDIS_PASSWORD="..."
fly secrets set PUBLIC_BASE_URL="https://url-shortener-service.fly.dev"
fly secrets set CORS_ALLOWED_ORIGINS="https://your-frontend.example.com"
```

Deploy:

```bash
fly deploy
```

Run migrations as a separate release step or one-off task before rolling traffic to code that depends on new schema.

