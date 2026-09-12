package middleware

import (
	"log/slog"
	"net/http"
	"strconv"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/prometheus/client_golang/prometheus"
)

type bucket struct {
	windowStart time.Time
	count       int
}

type FixedWindowLimiter struct {
	mu      sync.Mutex
	buckets map[string]bucket
}

func NewFixedWindowLimiter() *FixedWindowLimiter {
	return &FixedWindowLimiter{buckets: map[string]bucket{}}
}

func (l *FixedWindowLimiter) Allow(key string, limitPerMinute int, now time.Time) bool {
	if limitPerMinute <= 0 {
		return true
	}
	l.mu.Lock()
	defer l.mu.Unlock()

	current := l.buckets[key]
	if current.windowStart.IsZero() || now.Sub(current.windowStart) >= time.Minute {
		current = bucket{windowStart: now}
	}
	current.count++
	l.buckets[key] = current
	return current.count <= limitPerMinute
}

func FixedWindowRateLimit(limiter *FixedWindowLimiter, limitPerMinute int) gin.HandlerFunc {
	return func(c *gin.Context) {
		if !limiter.Allow(c.ClientIP(), limitPerMinute, time.Now().UTC()) {
			c.AbortWithStatusJSON(http.StatusTooManyRequests, gin.H{
				"error": gin.H{"message": "rate limit exceeded"},
			})
			return
		}
		c.Next()
	}
}

func RequestLogger(logger *slog.Logger) gin.HandlerFunc {
	return func(c *gin.Context) {
		start := time.Now()
		requestID := c.GetHeader("X-Request-ID")
		if requestID == "" {
			requestID = strconv.FormatInt(start.UnixNano(), 36)
		}
		c.Header("X-Request-ID", requestID)
		c.Set("request_id", requestID)
		c.Next()
		logger.Info("http request",
			"request_id", requestID,
			"method", c.Request.Method,
			"path", c.Request.URL.Path,
			"status", c.Writer.Status(),
			"latency_ms", time.Since(start).Milliseconds(),
			"client_ip", c.ClientIP(),
		)
	}
}

func PrometheusMetrics(counter *prometheus.CounterVec, histogram *prometheus.HistogramVec) gin.HandlerFunc {
	return func(c *gin.Context) {
		start := time.Now()
		c.Next()
		status := strconv.Itoa(c.Writer.Status())
		counter.WithLabelValues(c.Request.Method, c.FullPath(), status).Inc()
		histogram.WithLabelValues(c.Request.Method, c.FullPath()).Observe(time.Since(start).Seconds())
	}
}
