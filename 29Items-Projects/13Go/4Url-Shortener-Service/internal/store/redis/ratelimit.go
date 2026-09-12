package redis

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"strings"
	"time"

	redisclient "github.com/redis/go-redis/v9"
)

type RateLimiter struct {
	client *redisclient.Client
	limit  int64
	window time.Duration
}

func NewRateLimiter(client *redisclient.Client, limit int64, window time.Duration) *RateLimiter {
	return &RateLimiter{client: client, limit: limit, window: window}
}

func (r *RateLimiter) Allow(ctx context.Context, scope string, identifier string) (bool, int64, error) {
	if r.limit <= 0 {
		return true, 0, nil
	}

	key := fmt.Sprintf("ratelimit:%s:%s", sanitizeScope(scope), hashIdentifier(identifier))
	count, err := r.client.Incr(ctx, key).Result()
	if err != nil {
		return false, 0, fmt.Errorf("increment rate limit: %w", err)
	}
	if count == 1 {
		if err := r.client.Expire(ctx, key, r.window).Err(); err != nil {
			return false, 0, fmt.Errorf("expire rate limit: %w", err)
		}
	}

	remaining := r.limit - count
	if remaining < 0 {
		remaining = 0
	}
	return count <= r.limit, remaining, nil
}

func sanitizeScope(scope string) string {
	scope = strings.TrimSpace(strings.ToLower(scope))
	if scope == "" {
		return "default"
	}
	return strings.Map(func(r rune) rune {
		if r >= 'a' && r <= 'z' || r >= '0' && r <= '9' || r == '_' || r == '-' {
			return r
		}
		return '-'
	}, scope)
}

func hashIdentifier(identifier string) string {
	sum := sha256.Sum256([]byte(strings.TrimSpace(identifier)))
	return hex.EncodeToString(sum[:])
}
