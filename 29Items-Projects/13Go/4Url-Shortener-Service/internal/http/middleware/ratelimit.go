package middleware

import (
	"context"
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
)

type RateLimiter interface {
	Allow(ctx context.Context, scope string, identifier string) (bool, int64, error)
}

func RateLimit(limiter RateLimiter, scope string) gin.HandlerFunc {
	return func(c *gin.Context) {
		if limiter == nil {
			c.Next()
			return
		}

		identifier := c.ClientIP()
		if apiKey := c.GetHeader("X-API-Key"); apiKey != "" {
			identifier = apiKey
		}

		allowed, remaining, err := limiter.Allow(c.Request.Context(), scope, identifier)
		if err != nil {
			c.Header("X-RateLimit-Remaining", "0")
			c.AbortWithStatusJSON(http.StatusServiceUnavailable, gin.H{
				"error": gin.H{"code": "rate_limit_unavailable", "message": "rate limiter unavailable"},
			})
			return
		}

		c.Header("X-RateLimit-Remaining", strconv.FormatInt(remaining, 10))
		if !allowed {
			c.AbortWithStatusJSON(http.StatusTooManyRequests, gin.H{
				"error": gin.H{"code": "rate_limited", "message": "too many requests"},
			})
			return
		}

		c.Next()
	}
}
