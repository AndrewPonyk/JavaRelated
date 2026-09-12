package service

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/example/distributed-task-queue/internal/repository"
	"github.com/example/distributed-task-queue/pkg/task"
)

func TestCreateTaskEnqueuesAndHandlesIdempotency(t *testing.T) {
	repo := newFakeRepo()
	queue := newFakeQueue()
	service := NewTaskService(repo, queue, nil)

	req := task.CreateRequest{
		Type:           "example.email",
		Payload:        map[string]any{"recipient": "user@example.com"},
		MaxAttempts:    2,
		IdempotencyKey: "same-request",
	}

	first, err := service.Create(context.Background(), req)
	if err != nil {
		t.Fatalf("first create failed: %v", err)
	}
	second, err := service.Create(context.Background(), req)
	if err != nil {
		t.Fatalf("second create failed: %v", err)
	}

	if first.ID != second.ID {
		t.Fatalf("expected idempotent create to return same id, got %q and %q", first.ID, second.ID)
	}
	if len(queue.enqueued) != 1 {
		t.Fatalf("expected exactly one enqueue, got %d", len(queue.enqueued))
	}
}

func TestCreateRejectsUnsupportedTaskType(t *testing.T) {
	service := NewTaskService(newFakeRepo(), newFakeQueue(), nil)
	_, err := service.Create(context.Background(), task.CreateRequest{
		Type:    "unknown",
		Payload: map[string]any{"x": "y"},
	})
	if !errors.Is(err, ErrInvalidInput) {
		t.Fatalf("expected invalid input, got %v", err)
	}
}

func TestListRejectsInvalidStatus(t *testing.T) {
	service := NewTaskService(newFakeRepo(), newFakeQueue(), nil)
	_, err := service.List(context.Background(), repository.ListFilter{Status: task.Status("bad")})
	if !errors.Is(err, ErrInvalidInput) {
		t.Fatalf("expected invalid input, got %v", err)
	}
}

func TestGetRejectsInvalidID(t *testing.T) {
	service := NewTaskService(newFakeRepo(), newFakeQueue(), nil)
	_, err := service.Get(context.Background(), "not-a-uuid")
	if !errors.Is(err, ErrInvalidInput) {
		t.Fatalf("expected invalid input, got %v", err)
	}
}

func TestCancelUpdatesRepositoryAndQueue(t *testing.T) {
	repo := newFakeRepo()
	queue := newFakeQueue()
	service := NewTaskService(repo, queue, nil)
	created, err := service.Create(context.Background(), task.CreateRequest{
		Type:    "example.report",
		Payload: map[string]any{"name": "daily"},
	})
	if err != nil {
		t.Fatalf("create failed: %v", err)
	}

	if err := service.Cancel(context.Background(), created.ID); err != nil {
		t.Fatalf("cancel failed: %v", err)
	}
	item, err := repo.GetByID(context.Background(), created.ID)
	if err != nil {
		t.Fatalf("get failed: %v", err)
	}
	if item.Status != task.StatusCancelled {
		t.Fatalf("expected cancelled status, got %s", item.Status)
	}
	if len(queue.cancelled) != 1 {
		t.Fatalf("expected queue cancellation")
	}
}

func TestUpdateRequeuesPendingTask(t *testing.T) {
	repo := newFakeRepo()
	queue := newFakeQueue()
	service := NewTaskService(repo, queue, nil)
	created, err := service.Create(context.Background(), task.CreateRequest{
		Type:    "example.report",
		Payload: map[string]any{"name": "daily"},
	})
	if err != nil {
		t.Fatalf("create failed: %v", err)
	}

	updated, err := service.Update(context.Background(), created.ID, task.UpdateRequest{
		Type:    "example.email",
		Payload: map[string]any{"recipient": "ops@example.com"},
	})
	if err != nil {
		t.Fatalf("update failed: %v", err)
	}
	if updated.Type != "example.email" {
		t.Fatalf("expected updated type, got %s", updated.Type)
	}
	if len(queue.cancelled) != 1 || len(queue.enqueued) != 2 {
		t.Fatalf("expected cancellation and re-enqueue, got cancelled=%d enqueued=%d", len(queue.cancelled), len(queue.enqueued))
	}
}

func TestDeadLetterRequeue(t *testing.T) {
	repo := newFakeRepo()
	queue := newFakeQueue()
	service := NewTaskService(repo, queue, nil)
	created, err := service.Create(context.Background(), task.CreateRequest{
		Type:    "example.always_fail",
		Payload: map[string]any{},
	})
	if err != nil {
		t.Fatalf("create failed: %v", err)
	}
	if err := repo.MarkFailed(context.Background(), created.ID, task.StatusDeadLettered, "failed"); err != nil {
		t.Fatalf("mark failed: %v", err)
	}
	queue.dead = append(queue.dead, created.ID)

	items, err := service.DeadLetters(context.Background(), 10)
	if err != nil {
		t.Fatalf("dead letter list failed: %v", err)
	}
	if len(items) != 1 {
		t.Fatalf("expected one dead letter, got %d", len(items))
	}
	if err := service.RequeueDeadLetter(context.Background(), created.ID); err != nil {
		t.Fatalf("requeue failed: %v", err)
	}
	item, _ := repo.GetByID(context.Background(), created.ID)
	if item.Status != task.StatusRetrying {
		t.Fatalf("expected retrying status, got %s", item.Status)
	}
}

type fakeRepo struct {
	tasks       map[string]task.Task
	idempotency map[string]string
}

func newFakeRepo() *fakeRepo {
	return &fakeRepo{tasks: map[string]task.Task{}, idempotency: map[string]string{}}
}

func (r *fakeRepo) Create(ctx context.Context, item task.Task, timeoutSeconds int) (task.Task, bool, error) {
	if item.IdempotencyKey != "" {
		if id, ok := r.idempotency[item.IdempotencyKey]; ok {
			return r.tasks[id], false, nil
		}
		r.idempotency[item.IdempotencyKey] = item.ID
	}
	now := time.Now().UTC()
	item.CreatedAt = now
	item.UpdatedAt = now
	item.TimeoutSeconds = timeoutSeconds
	r.tasks[item.ID] = item
	return item, true, nil
}

func (r *fakeRepo) GetByID(ctx context.Context, id string) (task.Task, error) {
	item, ok := r.tasks[id]
	if !ok {
		return task.Task{}, repository.ErrNotFound
	}
	return item, nil
}

func (r *fakeRepo) ListByIDs(ctx context.Context, ids []string) ([]task.Task, error) {
	items := make([]task.Task, 0, len(ids))
	for _, id := range ids {
		item, err := r.GetByID(ctx, id)
		if err == nil {
			items = append(items, item)
		}
	}
	return items, nil
}

func (r *fakeRepo) List(ctx context.Context, filter repository.ListFilter) ([]task.Task, error) {
	items := make([]task.Task, 0, len(r.tasks))
	for _, item := range r.tasks {
		if filter.Status == "" || item.Status == filter.Status {
			items = append(items, item)
		}
	}
	return items, nil
}

func (r *fakeRepo) UpdateQueued(ctx context.Context, id string, req task.UpdateRequest) (task.Task, error) {
	item, err := r.GetByID(ctx, id)
	if err != nil {
		return task.Task{}, err
	}
	if req.Type != "" {
		item.Type = req.Type
	}
	if req.Payload != nil {
		item.Payload = req.Payload
	}
	r.tasks[id] = item
	return item, nil
}

func (r *fakeRepo) Delete(ctx context.Context, id string) error {
	delete(r.tasks, id)
	return nil
}

func (r *fakeRepo) MarkRunning(ctx context.Context, id string) (task.Task, int, error) {
	item, err := r.GetByID(ctx, id)
	if err != nil {
		return task.Task{}, 0, err
	}
	item.Status = task.StatusRunning
	item.Attempts++
	r.tasks[id] = item
	return item, item.Attempts, nil
}

func (r *fakeRepo) MarkCompleted(ctx context.Context, id string, result map[string]any) error {
	item, err := r.GetByID(ctx, id)
	if err != nil {
		return err
	}
	item.Status = task.StatusCompleted
	item.Result = result
	r.tasks[id] = item
	return nil
}

func (r *fakeRepo) MarkRetrying(ctx context.Context, id string, errText string) error {
	item, err := r.GetByID(ctx, id)
	if err != nil {
		return err
	}
	item.Status = task.StatusRetrying
	item.Error = errText
	r.tasks[id] = item
	return nil
}

func (r *fakeRepo) MarkFailed(ctx context.Context, id string, status task.Status, errText string) error {
	item, err := r.GetByID(ctx, id)
	if err != nil {
		return err
	}
	item.Status = status
	item.Error = errText
	r.tasks[id] = item
	return nil
}

func (r *fakeRepo) Cancel(ctx context.Context, id string) error {
	item, err := r.GetByID(ctx, id)
	if err != nil {
		return err
	}
	item.Status = task.StatusCancelled
	r.tasks[id] = item
	return nil
}

func (r *fakeRepo) Attempts(ctx context.Context, id string) ([]task.Attempt, error) {
	return []task.Attempt{}, nil
}

type fakeQueue struct {
	enqueued  []string
	cancelled []string
	dead      []string
}

func newFakeQueue() *fakeQueue {
	return &fakeQueue{}
}

func (q *fakeQueue) Enqueue(ctx context.Context, taskID string, runAt time.Time) error {
	q.enqueued = append(q.enqueued, taskID)
	return nil
}

func (q *fakeQueue) Reserve(ctx context.Context) (string, error) {
	return "", errors.New("not used")
}

func (q *fakeQueue) Ack(ctx context.Context, taskID string) error {
	return nil
}

func (q *fakeQueue) Retry(ctx context.Context, taskID string, runAt time.Time) error {
	return nil
}

func (q *fakeQueue) DeadLetter(ctx context.Context, taskID string) error {
	q.dead = append(q.dead, taskID)
	return nil
}

func (q *fakeQueue) Cancel(ctx context.Context, taskID string) error {
	q.cancelled = append(q.cancelled, taskID)
	return nil
}

func (q *fakeQueue) RequeueDeadLetter(ctx context.Context, taskID string) error {
	for index, id := range q.dead {
		if id == taskID {
			q.dead = append(q.dead[:index], q.dead[index+1:]...)
			q.enqueued = append(q.enqueued, taskID)
			return nil
		}
	}
	return nil
}

func (q *fakeQueue) DeadLetters(ctx context.Context, limit int64) ([]string, error) {
	return q.dead, nil
}

func (q *fakeQueue) PromoteDue(ctx context.Context, now time.Time, limit int64) (int64, error) {
	return 0, nil
}

func (q *fakeQueue) Stats(ctx context.Context) (task.QueueStats, error) {
	return task.QueueStats{Pending: int64(len(q.enqueued)), DeadLetters: int64(len(q.dead))}, nil
}
