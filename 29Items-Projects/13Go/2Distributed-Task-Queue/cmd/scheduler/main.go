package main

import (
	"context"
	"time"

	"github.com/example/distributed-task-queue/internal/config"
	"github.com/example/distributed-task-queue/internal/logging"
	"github.com/example/distributed-task-queue/internal/metrics"
	"github.com/example/distributed-task-queue/internal/queue"
	"github.com/example/distributed-task-queue/internal/shutdown"
)

func main() {
	ctx := shutdown.Context(context.Background())
	cfg := config.Load()
	logger := logging.New()
	if cfg.MetricsEnabled {
		metrics.Register()
	}
	redisClient := queue.NewRedisClient(cfg.RedisAddr, cfg.RedisPassword)
	taskQueue := queue.NewRedisQueue(redisClient, "tasks")

	logger.Info("scheduler started", "env", cfg.AppEnv)
	ticker := time.NewTicker(5 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			logger.Info("scheduler stopped")
			return
		case <-ticker.C:
			promoted, err := taskQueue.PromoteDue(ctx, time.Now().UTC(), 100)
			if err != nil {
				logger.Warn("failed to promote due tasks", "error", err)
				continue
			}
			stats, err := taskQueue.Stats(ctx)
			if err == nil {
				metrics.QueueDepth.WithLabelValues("pending").Set(float64(stats.Pending))
				metrics.QueueDepth.WithLabelValues("reserved").Set(float64(stats.Reserved))
				metrics.QueueDepth.WithLabelValues("retry").Set(float64(stats.Retry))
				metrics.QueueDepth.WithLabelValues("dead").Set(float64(stats.DeadLetters))
			}
			logger.Info("scheduler promoted due tasks", "count", promoted)
		}
	}
}
