package worker

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"sync"
	"time"

	"github.com/example/distributed-task-queue/internal/messaging"
	"github.com/example/distributed-task-queue/internal/metrics"
	"github.com/example/distributed-task-queue/internal/queue"
	"github.com/example/distributed-task-queue/internal/repository"
	"github.com/example/distributed-task-queue/pkg/task"
)

type Pool struct {
	concurrency int
	queue       queue.Queue
	repo        repository.TaskRepository
	processor   *Processor
	bus         messaging.EventBus
	logger      *slog.Logger
}

func NewPool(concurrency int, queue queue.Queue, repo repository.TaskRepository, processor *Processor, bus messaging.EventBus, logger *slog.Logger) *Pool {
	if concurrency <= 0 {
		concurrency = 1
	}
	return &Pool{
		concurrency: concurrency,
		queue:       queue,
		repo:        repo,
		processor:   processor,
		bus:         bus,
		logger:      logger,
	}
}

func (p *Pool) Run(ctx context.Context) error {
	var wg sync.WaitGroup
	for i := 0; i < p.concurrency; i++ {
		workerID := i + 1
		wg.Add(1)
		go func() {
			defer wg.Done()
			p.runWorker(ctx, workerID)
		}()
	}

	<-ctx.Done()
	wg.Wait()
	return ctx.Err()
}

func (p *Pool) runWorker(ctx context.Context, workerID int) {
	for {
		select {
		case <-ctx.Done():
			p.logger.Info("worker stopped", "worker_id", workerID)
			return
		default:
		}

		taskID, err := p.queue.Reserve(ctx)
		if err != nil {
			if errors.Is(err, context.Canceled) {
				return
			}
			if !errors.Is(err, queue.ErrEmpty) {
				p.logger.Warn("failed to reserve task", "worker_id", workerID, "error", err)
			}
			sleep(ctx, 500*time.Millisecond)
			continue
		}

		p.handleTask(ctx, workerID, taskID)
	}
}

func (p *Pool) handleTask(ctx context.Context, workerID int, taskID string) {
	item, _, err := p.repo.MarkRunning(ctx, taskID)
	if err != nil {
		p.logger.Warn("reserved task is not runnable", "worker_id", workerID, "task_id", taskID, "error", err)
		_ = p.queue.Ack(ctx, taskID)
		return
	}

	p.publish(ctx, "task.started", item)
	start := time.Now()
	timeout := time.Duration(item.TimeoutSeconds) * time.Second
	if timeout <= 0 {
		timeout = 30 * time.Second
	}
	taskCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	result, processErr := p.processor.Process(taskCtx, item)
	metrics.TaskDuration.WithLabelValues(item.Type).Observe(time.Since(start).Seconds())

	if processErr == nil {
		if err := p.repo.MarkCompleted(ctx, item.ID, result); err != nil {
			if errors.Is(err, repository.ErrStateConflict) || errors.Is(err, repository.ErrNotFound) {
				_ = p.queue.Ack(ctx, item.ID)
				p.logger.Info("task completion ignored because state changed", "task_id", item.ID)
				return
			}
			p.logger.Error("failed to mark task completed", "task_id", item.ID, "error", err)
			_ = p.queue.Retry(ctx, item.ID, nextRetryAt(item.Attempts))
			return
		}
		_ = p.queue.Ack(ctx, item.ID)
		metrics.TasksProcessed.WithLabelValues(string(task.StatusCompleted)).Inc()
		p.publish(ctx, "task.completed", map[string]any{"id": item.ID, "result": result})
		return
	}

	errText := processErr.Error()
	if errors.Is(processErr, context.DeadlineExceeded) {
		errText = "task timeout exceeded"
	}

	if item.Attempts >= item.MaxAttempts {
		if err := p.repo.MarkFailed(ctx, item.ID, task.StatusDeadLettered, errText); err != nil {
			if errors.Is(err, repository.ErrStateConflict) || errors.Is(err, repository.ErrNotFound) {
				_ = p.queue.Ack(ctx, item.ID)
				p.logger.Info("task failure ignored because state changed", "task_id", item.ID)
				return
			}
			p.logger.Error("failed to mark task dead-lettered", "task_id", item.ID, "error", err)
			_ = p.queue.Retry(ctx, item.ID, nextRetryAt(item.Attempts))
			return
		}
		_ = p.queue.DeadLetter(ctx, item.ID)
		metrics.TasksProcessed.WithLabelValues(string(task.StatusDeadLettered)).Inc()
		p.publish(ctx, "task.dead_lettered", map[string]any{"id": item.ID, "error": errText})
		return
	}

	retryAt := nextRetryAt(item.Attempts)
	if err := p.repo.MarkRetrying(ctx, item.ID, errText); err != nil {
		if errors.Is(err, repository.ErrStateConflict) || errors.Is(err, repository.ErrNotFound) {
			_ = p.queue.Ack(ctx, item.ID)
			p.logger.Info("task retry ignored because state changed", "task_id", item.ID)
			return
		}
		p.logger.Error("failed to mark task retrying", "task_id", item.ID, "error", err)
	}
	_ = p.queue.Retry(ctx, item.ID, retryAt)
	metrics.TasksProcessed.WithLabelValues(string(task.StatusRetrying)).Inc()
	p.publish(ctx, "task.failed", map[string]any{"id": item.ID, "error": errText, "retry_at": retryAt})
}

func nextRetryAt(attempt int) time.Time {
	if attempt < 1 {
		attempt = 1
	}
	delay := time.Duration(1<<min(attempt-1, 6)) * time.Second
	return time.Now().UTC().Add(delay)
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

func sleep(ctx context.Context, duration time.Duration) {
	timer := time.NewTimer(duration)
	defer timer.Stop()
	select {
	case <-ctx.Done():
	case <-timer.C:
	}
}

func (p *Pool) publish(ctx context.Context, subject string, value any) {
	if p.bus == nil {
		return
	}
	payload, err := json.Marshal(value)
	if err != nil {
		return
	}
	_ = p.bus.Publish(ctx, subject, payload)
}
