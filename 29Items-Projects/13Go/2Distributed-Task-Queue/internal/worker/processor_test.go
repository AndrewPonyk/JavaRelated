package worker

import (
	"context"
	"errors"
	"log/slog"
	"testing"

	"github.com/example/distributed-task-queue/pkg/task"
)

func TestProcessorProcessesEmail(t *testing.T) {
	processor := NewProcessor(slog.Default())
	result, err := processor.Process(context.Background(), task.Task{
		ID:       "task-1",
		Type:     "example.email",
		Payload:  map[string]any{"recipient": "user@example.com"},
		Attempts: 1,
	})
	if err != nil {
		t.Fatalf("process failed: %v", err)
	}
	if result["sent"] != true {
		t.Fatalf("expected sent result, got %#v", result)
	}
}

func TestProcessorFailOnceIsRetryableOnFirstAttempt(t *testing.T) {
	processor := NewProcessor(slog.Default())
	_, err := processor.Process(context.Background(), task.Task{
		ID:       "task-1",
		Type:     "example.fail_once",
		Payload:  map[string]any{},
		Attempts: 1,
	})
	if !errors.Is(err, ErrRetryable) {
		t.Fatalf("expected retryable error, got %v", err)
	}
}

func TestProcessorRejectsInvalidWebhook(t *testing.T) {
	processor := NewProcessor(slog.Default())
	_, err := processor.Process(context.Background(), task.Task{
		ID:       "task-1",
		Type:     "example.webhook",
		Payload:  map[string]any{"url": "not-a-url"},
		Attempts: 1,
	})
	if err == nil {
		t.Fatalf("expected invalid webhook error")
	}
}
