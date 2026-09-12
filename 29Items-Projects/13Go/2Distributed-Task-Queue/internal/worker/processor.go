package worker

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/url"
	"strings"
	"time"

	"github.com/example/distributed-task-queue/pkg/task"
)

var ErrRetryable = errors.New("retryable task failure")

type Processor struct {
	logger *slog.Logger
}

func NewProcessor(logger *slog.Logger) *Processor {
	return &Processor{logger: logger}
}

func (p *Processor) Process(ctx context.Context, item task.Task) (map[string]any, error) {
	p.logger.Info("processing task", "task_id", item.ID, "task_type", item.Type, "attempt", item.Attempts)

	switch item.Type {
	case "example.email":
		return p.processEmail(ctx, item)
	case "example.webhook":
		return p.processWebhook(ctx, item)
	case "example.report":
		return p.processReport(ctx, item)
	case "example.fail_once":
		return p.processFailOnce(ctx, item)
	case "example.always_fail":
		return nil, fmt.Errorf("%w: requested permanent failure", ErrRetryable)
	default:
		return nil, fmt.Errorf("unsupported task type %q", item.Type)
	}
}

func (p *Processor) processEmail(ctx context.Context, item task.Task) (map[string]any, error) {
	recipient, ok := stringField(item.Payload, "recipient")
	if !ok || !strings.Contains(recipient, "@") {
		return nil, errors.New("payload.recipient must be a valid email-like value")
	}
	if err := wait(ctx, 100*time.Millisecond); err != nil {
		return nil, err
	}
	return map[string]any{"sent": true, "recipient": recipient}, nil
}

func (p *Processor) processWebhook(ctx context.Context, item task.Task) (map[string]any, error) {
	target, ok := stringField(item.Payload, "url")
	if !ok {
		return nil, errors.New("payload.url is required")
	}
	parsed, err := url.ParseRequestURI(target)
	if err != nil || parsed.Scheme == "" || parsed.Host == "" {
		return nil, errors.New("payload.url must be absolute")
	}
	if err := wait(ctx, 150*time.Millisecond); err != nil {
		return nil, err
	}
	return map[string]any{"delivered": true, "url": target}, nil
}

func (p *Processor) processReport(ctx context.Context, item task.Task) (map[string]any, error) {
	name, ok := stringField(item.Payload, "name")
	if !ok || strings.TrimSpace(name) == "" {
		return nil, errors.New("payload.name is required")
	}
	if err := wait(ctx, 200*time.Millisecond); err != nil {
		return nil, err
	}
	return map[string]any{"generated": true, "name": name}, nil
}

func (p *Processor) processFailOnce(ctx context.Context, item task.Task) (map[string]any, error) {
	if item.Attempts == 1 {
		return nil, fmt.Errorf("%w: first attempt intentionally failed", ErrRetryable)
	}
	if err := wait(ctx, 50*time.Millisecond); err != nil {
		return nil, err
	}
	return map[string]any{"recovered": true}, nil
}

func stringField(payload map[string]any, name string) (string, bool) {
	value, ok := payload[name]
	if !ok {
		return "", false
	}
	text, ok := value.(string)
	return text, ok
}

func wait(ctx context.Context, duration time.Duration) error {
	timer := time.NewTimer(duration)
	defer timer.Stop()

	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}
