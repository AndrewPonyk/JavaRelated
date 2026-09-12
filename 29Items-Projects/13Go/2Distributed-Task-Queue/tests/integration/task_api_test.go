package integration

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/example/distributed-task-queue/internal/api"
	"github.com/example/distributed-task-queue/internal/repository"
	"github.com/example/distributed-task-queue/internal/service"
	"github.com/example/distributed-task-queue/pkg/task"
)

func TestTaskAPIFlow(t *testing.T) {
	repo := newHTTPRepo()
	queue := &httpQueue{}
	taskService := service.NewTaskService(repo, queue, nil)
	server := httptest.NewServer(api.NewRouter(api.NewHandler(taskService), slog.Default(), "test-key", false))
	defer server.Close()

	createBody := []byte(`{"type":"example.email","payload":{"recipient":"user@example.com"},"max_attempts":3}`)
	req, err := http.NewRequest(http.MethodPost, server.URL+"/api/v1/tasks", bytes.NewReader(createBody))
	if err != nil {
		t.Fatalf("create request failed: %v", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-API-Key", "test-key")
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("create call failed: %v", err)
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusAccepted {
		t.Fatalf("expected 202, got %d", res.StatusCode)
	}

	var created task.CreateResponse
	if err := json.NewDecoder(res.Body).Decode(&created); err != nil {
		t.Fatalf("decode create response failed: %v", err)
	}

	getReq, _ := http.NewRequest(http.MethodGet, server.URL+"/api/v1/tasks/"+created.ID, nil)
	getReq.Header.Set("X-API-Key", "test-key")
	getRes, err := http.DefaultClient.Do(getReq)
	if err != nil {
		t.Fatalf("get call failed: %v", err)
	}
	defer getRes.Body.Close()
	if getRes.StatusCode != http.StatusOK {
		t.Fatalf("expected 200, got %d", getRes.StatusCode)
	}

	cancelReq, _ := http.NewRequest(http.MethodPost, server.URL+"/api/v1/tasks/"+created.ID+"/cancel", nil)
	cancelReq.Header.Set("X-API-Key", "test-key")
	cancelRes, err := http.DefaultClient.Do(cancelReq)
	if err != nil {
		t.Fatalf("cancel call failed: %v", err)
	}
	defer cancelRes.Body.Close()
	if cancelRes.StatusCode != http.StatusNoContent {
		t.Fatalf("expected 204, got %d", cancelRes.StatusCode)
	}

	statsReq, _ := http.NewRequest(http.MethodGet, server.URL+"/api/v1/queue/stats", nil)
	statsReq.Header.Set("X-API-Key", "test-key")
	statsRes, err := http.DefaultClient.Do(statsReq)
	if err != nil {
		t.Fatalf("stats call failed: %v", err)
	}
	defer statsRes.Body.Close()
	if statsRes.StatusCode != http.StatusOK {
		t.Fatalf("expected stats 200, got %d", statsRes.StatusCode)
	}
}

func TestTaskAPIRequiresAPIKey(t *testing.T) {
	repo := newHTTPRepo()
	queue := &httpQueue{}
	taskService := service.NewTaskService(repo, queue, nil)
	server := httptest.NewServer(api.NewRouter(api.NewHandler(taskService), slog.Default(), "test-key", false))
	defer server.Close()

	res, err := http.Get(server.URL + "/api/v1/tasks")
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", res.StatusCode)
	}
}

func TestTaskAPIRejectsInvalidStatus(t *testing.T) {
	repo := newHTTPRepo()
	queue := &httpQueue{}
	taskService := service.NewTaskService(repo, queue, nil)
	server := httptest.NewServer(api.NewRouter(api.NewHandler(taskService), slog.Default(), "test-key", false))
	defer server.Close()

	req, _ := http.NewRequest(http.MethodGet, server.URL+"/api/v1/tasks?status=bad", nil)
	req.Header.Set("X-API-Key", "test-key")
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", res.StatusCode)
	}
}

type httpRepo struct {
	tasks map[string]task.Task
}

func newHTTPRepo() *httpRepo {
	return &httpRepo{tasks: map[string]task.Task{}}
}

func (r *httpRepo) Create(ctx context.Context, item task.Task, timeoutSeconds int) (task.Task, bool, error) {
	now := time.Now().UTC()
	item.CreatedAt = now
	item.UpdatedAt = now
	item.TimeoutSeconds = timeoutSeconds
	r.tasks[item.ID] = item
	return item, true, nil
}

func (r *httpRepo) GetByID(ctx context.Context, id string) (task.Task, error) {
	item, ok := r.tasks[id]
	if !ok {
		return task.Task{}, repository.ErrNotFound
	}
	return item, nil
}

func (r *httpRepo) ListByIDs(ctx context.Context, ids []string) ([]task.Task, error) {
	items := make([]task.Task, 0, len(ids))
	for _, id := range ids {
		item, err := r.GetByID(ctx, id)
		if err == nil {
			items = append(items, item)
		}
	}
	return items, nil
}

func (r *httpRepo) List(ctx context.Context, filter repository.ListFilter) ([]task.Task, error) {
	items := make([]task.Task, 0, len(r.tasks))
	for _, item := range r.tasks {
		items = append(items, item)
	}
	return items, nil
}

func (r *httpRepo) UpdateQueued(ctx context.Context, id string, req task.UpdateRequest) (task.Task, error) {
	item, err := r.GetByID(ctx, id)
	if err != nil {
		return task.Task{}, err
	}
	r.tasks[id] = item
	return item, nil
}

func (r *httpRepo) Delete(ctx context.Context, id string) error {
	delete(r.tasks, id)
	return nil
}

func (r *httpRepo) MarkRunning(ctx context.Context, id string) (task.Task, int, error) {
	return task.Task{}, 0, errors.New("not used")
}

func (r *httpRepo) MarkCompleted(ctx context.Context, id string, result map[string]any) error {
	return nil
}

func (r *httpRepo) MarkRetrying(ctx context.Context, id string, errText string) error {
	return nil
}

func (r *httpRepo) MarkFailed(ctx context.Context, id string, status task.Status, errText string) error {
	return nil
}

func (r *httpRepo) Cancel(ctx context.Context, id string) error {
	item, err := r.GetByID(ctx, id)
	if err != nil {
		return err
	}
	item.Status = task.StatusCancelled
	r.tasks[id] = item
	return nil
}

func (r *httpRepo) Attempts(ctx context.Context, id string) ([]task.Attempt, error) {
	return []task.Attempt{}, nil
}

type httpQueue struct{}

func (q *httpQueue) Enqueue(ctx context.Context, taskID string, runAt time.Time) error { return nil }
func (q *httpQueue) Reserve(ctx context.Context) (string, error)                      { return "", errors.New("not used") }
func (q *httpQueue) Ack(ctx context.Context, taskID string) error                     { return nil }
func (q *httpQueue) Retry(ctx context.Context, taskID string, runAt time.Time) error  { return nil }
func (q *httpQueue) DeadLetter(ctx context.Context, taskID string) error              { return nil }
func (q *httpQueue) Cancel(ctx context.Context, taskID string) error                  { return nil }
func (q *httpQueue) RequeueDeadLetter(ctx context.Context, taskID string) error       { return nil }
func (q *httpQueue) DeadLetters(ctx context.Context, limit int64) ([]string, error)   { return []string{}, nil }
func (q *httpQueue) PromoteDue(ctx context.Context, now time.Time, limit int64) (int64, error) {
	return 0, nil
}
func (q *httpQueue) Stats(ctx context.Context) (task.QueueStats, error) {
	return task.QueueStats{}, nil
}
