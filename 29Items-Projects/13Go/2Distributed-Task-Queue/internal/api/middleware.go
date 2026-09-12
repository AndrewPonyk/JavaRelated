package api

import (
	"log/slog"
	"net/http"
	"strings"
	"sync"
	"time"
)

func RequestLogger(logger *slog.Logger) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			start := time.Now()
			next.ServeHTTP(w, r)
			logger.Info("request completed",
				"method", r.Method,
				"path", r.URL.Path,
				"duration_ms", time.Since(start).Milliseconds(),
			)
		})
	}
}

func SecurityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("X-Frame-Options", "DENY")
		w.Header().Set("Referrer-Policy", "no-referrer")
		next.ServeHTTP(w, r)
	})
}

func EnforceHTTPS(enabled bool) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if enabled && r.TLS == nil && r.Header.Get("X-Forwarded-Proto") != "https" {
				writeError(w, http.StatusUpgradeRequired, "https is required")
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

func APIKeyAuth(apiKey string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if apiKey == "" {
				writeError(w, http.StatusInternalServerError, "api authentication is not configured")
				return
			}
			if r.Header.Get("X-API-Key") != apiKey {
				writeError(w, http.StatusUnauthorized, "missing or invalid API key")
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

func RateLimit(limit int, window time.Duration) func(http.Handler) http.Handler {
	type bucket struct {
		count   int
		resetAt time.Time
	}
	var mu sync.Mutex
	buckets := map[string]bucket{}

	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			key := clientKey(r)
			now := time.Now()

			mu.Lock()
			current := buckets[key]
			if current.resetAt.IsZero() || now.After(current.resetAt) {
				current = bucket{resetAt: now.Add(window)}
			}
			current.count++
			buckets[key] = current
			if len(buckets) > 10000 {
				for bucketKey, bucketValue := range buckets {
					if now.After(bucketValue.resetAt) {
						delete(buckets, bucketKey)
					}
				}
			}
			allowed := current.count <= limit
			mu.Unlock()

			if !allowed {
				writeError(w, http.StatusTooManyRequests, "rate limit exceeded")
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

func clientKey(r *http.Request) string {
	if forwarded := r.Header.Get("X-Forwarded-For"); forwarded != "" {
		return strings.TrimSpace(strings.Split(forwarded, ",")[0])
	}
	return r.RemoteAddr
}
