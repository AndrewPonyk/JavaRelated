package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"time"

	"github.com/example/distributed-task-queue/internal/api"
	"github.com/example/distributed-task-queue/internal/config"
	"github.com/example/distributed-task-queue/internal/db"
	"github.com/example/distributed-task-queue/internal/logging"
	"github.com/example/distributed-task-queue/internal/messaging"
	"github.com/example/distributed-task-queue/internal/metrics"
	"github.com/example/distributed-task-queue/internal/queue"
	"github.com/example/distributed-task-queue/internal/repository"
	"github.com/example/distributed-task-queue/internal/service"
	"github.com/example/distributed-task-queue/internal/shutdown"
)

func main() {
	ctx := shutdown.Context(context.Background())
	cfg := config.Load()
	logger := logging.New()
	if err := cfg.ValidateAPI(); err != nil {
		logger.Error("invalid api configuration", "error", err)
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
	if err := db.Wait(ctx, pool, time.Second); err != nil {
		logger.Error("postgres did not become ready", "error", err)
		return
	}
	if cfg.AutoMigrate {
		if err := db.Migrate(ctx, pool, "migrations"); err != nil {
			logger.Error("database migration failed", "error", err)
			return
		}
	}

	bus, err := messaging.Connect(cfg.NATSURL)
	if err != nil {
		logger.Warn("nats unavailable; continuing without event publishing", "error", err)
	}
	if bus != nil {
		defer bus.Close()
	}

	repo := repository.NewPostgresTaskRepository(pool)
	taskService := service.NewTaskService(repo, taskQueue, bus)
	router := api.NewRouter(api.NewHandler(taskService), logger, cfg.APIKey, cfg.EnforceHTTPS)

	server := &http.Server{
		Addr:              cfg.HTTPAddr,
		Handler:           router,
		ReadHeaderTimeout: 5 * time.Second,
	}

	go func() {
		logger.Info("api listening", "addr", cfg.HTTPAddr)
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			logger.Error("api server failed", slog.Any("error", err))
		}
	}()

	<-ctx.Done()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), cfg.ShutdownTimeout)
	defer cancel()
	_ = server.Shutdown(shutdownCtx)
	logger.Info("api stopped")
}
