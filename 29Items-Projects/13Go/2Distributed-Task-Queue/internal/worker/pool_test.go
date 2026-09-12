package worker

import (
	"context"
	"errors"
	"log/slog"
	"testing"
	"time"

	"github.com/example/distributed-task-queue/internal/repository"
	"github.com/example/distributed-task-queue/pkg/task"
)

func TestPoolCompletesTask(t *testing.T) {
	repo := newPoolRepo(task.Task{
		ID:             "00000000-0000-4000-8000-000000000001",
		Type:           "example.email",
		Payload:        map[string]any{"recipient": "user@example.com"},
		Status:         task.StatusPending,
		MaxAttempts:    3,
		TimeoutSeconds: 5,
	})
	queue := &poolQueue{}
	pool := NewPool(1, queue, repo, NewProcessor(slog.Default()), nil, slog.Default())

	pool.handleTask(context.Background(), 1, "00000000-0000-4000-8000-000000000001")

	item := repo.tasks["00000000-0000-4000-8000-000000000001"]
	if item.Status != task.StatusCompleted {
		t.Fatalf("expected completed status, got %s", item.Status)
	}
	if len(queue.acked) != 1 {
		t.Fatalf("expected queue ack")
	}
}

func TestPoolDeadLettersAfterMaxAttempts(t *testing.T) {
	repo := newPoolRepo(task.Task{
		ID:             "00000000-0000-4000-8000-000000000002",
		Type:           "example.always_fail",
		Payload:        map[string]any{},
		Status:         task.StatusPending,
		MaxAttempts:    1,
		TimeoutSeconds: 5,
	})
	queue := &poolQueue{}
	pool := NewPool(1, queue, repo, NewProcessor(slog.Default()), nil, slog.Default())

	pool.handleTask(context.Background(), 1, "00000000-0000-4000-8000-000000000002")

	item := repo.tasks["00000000-0000-4000-8000-000000000002"]
	if item.Status != task.StatusDeadLettered {
		t.Fatalf("expected dead-lettered status, got %s", item.Status)
	}
	if len(queue.dead) != 1 {
		t.Fatalf("expected dead-letter queue movement")
	}
}

type poolRepo struct {
	tasks map[string]task.Task
}

func newPoolRepo(items ...task.Task) *poolRepo {
	repo := &poolRepo{tasks: map[string]task.Task{}}
	for _, item := range items {
		repo.tasks[item.ID] = item
	}
	return repo
}

func (r *poolRepo) Create(ctx context.Context, item task.Task, timeoutSeconds int) (task.Task, bool, error) {
	r.tasks[item.ID] = item
	return item, true, nil
}

func (r *poolRepo) GetByID(ctx context.Context, id string) (task.Task, error) {
	item, ok := r.tasks[id]
	if !ok {
		return task.Task{}, repository.ErrNotFound
	}
	return item, nil
}

func (r *poolRepo) ListByIDs(ctx context.Context, ids []string) ([]task.Task, error) {
	return []task.Task{}, nil
}

func (r *poolRepo) List(ctx context.Context, filter repository.ListFilter) ([]task.Task, error) {
	return []task.Task{}, nil
}

func (r *poolRepo) UpdateQueued(ctx context.Context, id string, req task.UpdateRequest) (task.Task, error) {
	return task.Task{}, nil
}

func (r *poolRepo) Delete(ctx context.Context, id string) error {
	delete(r.tasks, id)
	return nil
}

func (r *poolRepo) MarkRunning(ctx context.Context, id string) (task.Task, int, error) {
	item, err := r.GetByID(ctx, id)
	if err != nil {
		return task.Task{}, 0, err
	}
	item.Status = task.StatusRunning
	item.Attempts++
	r.tasks[id] = item
	return item, item.Attempts, nil
}

func (r *poolRepo) MarkCompleted(ctx context.Context, id string, result map[string]any) error {
	item, err := r.GetByID(ctx, id)
	if err != nil {
		return err
	}
	item.Status = task.StatusCompleted
	item.Result = result
	r.tasks[id] = item
	return nil
}

func (r *poolRepo) MarkRetrying(ctx context.Context, id string, errText string) error {
	item, err := r.GetByID(ctx, id)
	if err != nil {
		return err
	}
	item.Status = task.StatusRetrying
	r.tasks[id] = item
	return nil
}

func (r *poolRepo) MarkFailed(ctx context.Context, id string, status task.Status, errText string) error {
	item, err := r.GetByID(ctx, id)
	if err != nil {
		return err
	}
	item.Status = status
	item.Error = errText
	r.tasks[id] = item
	return nil
}

func (r *poolRepo) Cancel(ctx context.Context, id string) error {
	return nil
}

func (r *poolRepo) Attempts(ctx context.Context, id string) ([]task.Attempt, error) {
	return []task.Attempt{}, nil
}

type poolQueue struct {
	acked []string
	dead  []string
}

func (q *poolQueue) Enqueue(ctx context.Context, taskID string, runAt time.Time) error {
	return nil
}

func (q *poolQueue) Reserve(ctx context.Context) (string, error) {
	return "", errors.New("not used")
}

func (q *poolQueue) Ack(ctx context.Context, taskID string) error {
	q.acked = append(q.acked, taskID)
	return nil
}

func (q *poolQueue) Retry(ctx context.Context, taskID string, runAt time.Time) error {
	return nil
}

func (q *poolQueue) DeadLetter(ctx context.Context, taskID string) error {
	q.dead = append(q.dead, taskID)
	return nil
}

func (q *poolQueue) Cancel(ctx context.Context, taskID string) error {
	return nil
}

func (q *poolQueue) RequeueDeadLetter(ctx context.Context, taskID string) error {
	return nil
}

func (q *poolQueue) DeadLetters(ctx context.Context, limit int64) ([]string, error) {
	return q.dead, nil
}

func (q *poolQueue) PromoteDue(ctx context.Context, now time.Time, limit int64) (int64, error) {
	return 0, nil
}

func (q *poolQueue) Stats(ctx context.Context) (task.QueueStats, error) {
	return task.QueueStats{}, nil
}
