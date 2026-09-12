package main

import (
	"context"
	"errors"

	"github.com/example/distributed-task-queue/internal/config"
	"github.com/example/distributed-task-queue/internal/db"
	"github.com/example/distributed-task-queue/internal/logging"
	"github.com/example/distributed-task-queue/internal/messaging"
	"github.com/example/distributed-task-queue/internal/metrics"
	"github.com/example/distributed-task-queue/internal/queue"
	"github.com/example/distributed-task-queue/internal/repository"
	"github.com/example/distributed-task-queue/internal/shutdown"
	workerpkg "github.com/example/distributed-task-queue/internal/worker"
)

func main() {
	ctx := shutdown.Context(context.Background())
	cfg := config.Load()
	logger := logging.New()
	if cfg.DatabaseURL == "" {
		logger.Error("invalid worker configuration", "error", "DATABASE_URL is required")
		return
	}

	if cfg.MetricsEnabled {
		metrics.Register()
	}

	redisClient := queue.NewRedisClient(cfg.RedisAddr, cfg.RedisPassword)
	taskQueue := queue.NewRedisQueue(redisClient, "tasks")

	pool, err := db.Connect(ctx, cfg.DatabaseURL)
	if err != nil {
		logger.Error("failed to configure postgres connection", "error", err)
		return
	}
	defer pool.Close()
	if err := db.Wait(ctx, pool, 0); err != nil {
		logger.Error("postgres did not become ready", "error", err)
		return
	}

	bus, err := messaging.Connect(cfg.NATSURL)
	if err != nil {
		logger.Warn("nats unavailable; continuing without event publishing", "error", err)
	}
	if bus != nil {
		defer bus.Close()
	}

	repo := repository.NewPostgresTaskRepository(pool)
	processor := workerpkg.NewProcessor(logger)
	workerPool := workerpkg.NewPool(cfg.WorkerConcurrency, taskQueue, repo, processor, bus, logger)

	if err := workerPool.Run(ctx); err != nil && !errors.Is(err, context.Canceled) {
		logger.Error("worker pool stopped with error", "error", err)
	}
}
