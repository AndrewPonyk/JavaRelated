package service

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"regexp"
	"strings"
	"time"

	"github.com/example/distributed-task-queue/internal/messaging"
	"github.com/example/distributed-task-queue/internal/queue"
	"github.com/example/distributed-task-queue/internal/repository"
	"github.com/example/distributed-task-queue/pkg/task"
)

var (
	ErrInvalidInput = errors.New("invalid input")
	ErrConflict     = errors.New("conflict")
	uuidPattern     = regexp.MustCompile(`^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$`)
)

type TaskService struct {
	repo  repository.TaskRepository
	queue queue.Queue
	bus   messaging.EventBus
}

func NewTaskService(repo repository.TaskRepository, queue queue.Queue, bus messaging.EventBus) *TaskService {
	return &TaskService{repo: repo, queue: queue, bus: bus}
}

func (s *TaskService) Create(ctx context.Context, req task.CreateRequest) (task.CreateResponse, error) {
	req.Type = strings.TrimSpace(req.Type)
	if err := validateCreate(req); err != nil {
		return task.CreateResponse{}, err
	}
	if req.MaxAttempts <= 0 {
		req.MaxAttempts = 3
	}
	if req.TimeoutSeconds <= 0 {
		req.TimeoutSeconds = 30
	}

	runAt := time.Now().UTC()
	if req.DelaySeconds > 0 {
		runAt = runAt.Add(time.Duration(req.DelaySeconds) * time.Second)
	}

	id, err := newID()
	if err != nil {
		return task.CreateResponse{}, err
	}

	item := task.Task{
		ID:             id,
		Type:           req.Type,
		Payload:        req.Payload,
		Status:         task.StatusPending,
		MaxAttempts:    req.MaxAttempts,
		IdempotencyKey: strings.TrimSpace(req.IdempotencyKey),
		RunAt:          runAt,
		TimeoutSeconds: req.TimeoutSeconds,
	}

	created, isNew, err := s.repo.Create(ctx, item, req.TimeoutSeconds)
	if err != nil {
		return task.CreateResponse{}, err
	}
	if isNew {
		if err := s.queue.Enqueue(ctx, created.ID, created.RunAt); err != nil {
			return task.CreateResponse{}, err
		}
		s.publish(ctx, "task.created", created)
	}

	return task.CreateResponse{ID: created.ID, Status: created.Status}, nil
}

func (s *TaskService) Get(ctx context.Context, id string) (task.Task, error) {
	if err := validateTaskID(id); err != nil {
		return task.Task{}, err
	}
	return s.repo.GetByID(ctx, id)
}

func (s *TaskService) List(ctx context.Context, filter repository.ListFilter) ([]task.Task, error) {
	if filter.Limit <= 0 || filter.Limit > 200 {
		filter.Limit = 50
	}
	if filter.Status != "" && !task.IsValidStatus(filter.Status) {
		return nil, fmt.Errorf("%w: invalid task status", ErrInvalidInput)
	}
	return s.repo.List(ctx, filter)
}

func (s *TaskService) Update(ctx context.Context, id string, req task.UpdateRequest) (task.Task, error) {
	if err := validateTaskID(id); err != nil {
		return task.Task{}, err
	}
	req.Type = strings.TrimSpace(req.Type)
	if req.Type != "" && !validTaskType(req.Type) {
		return task.Task{}, fmt.Errorf("%w: unsupported task type", ErrInvalidInput)
	}
	if req.MaxAttempts < 0 || req.MaxAttempts > 20 || req.DelaySeconds < 0 || req.DelaySeconds > 604800 || req.TimeoutSeconds < 0 || req.TimeoutSeconds > 3600 {
		return task.Task{}, fmt.Errorf("%w: numeric values are outside allowed bounds", ErrInvalidInput)
	}
	updated, err := s.repo.UpdateQueued(ctx, id, req)
	if err != nil {
		if errors.Is(err, repository.ErrNotFound) {
			return task.Task{}, err
		}
		return task.Task{}, fmt.Errorf("%w: %v", ErrConflict, err)
	}
	if err := s.queue.Cancel(ctx, id); err != nil {
		return task.Task{}, err
	}
	if err := s.queue.Enqueue(ctx, updated.ID, updated.RunAt); err != nil {
		return task.Task{}, err
	}
	s.publish(ctx, "task.updated", updated)
	return updated, nil
}

func (s *TaskService) Delete(ctx context.Context, id string) error {
	if err := validateTaskID(id); err != nil {
		return err
	}
	if err := s.queue.Cancel(ctx, id); err != nil {
		return err
	}
	if err := s.repo.Delete(ctx, id); err != nil {
		return err
	}
	s.publish(ctx, "task.deleted", map[string]string{"id": id})
	return nil
}

func (s *TaskService) Cancel(ctx context.Context, id string) error {
	if err := validateTaskID(id); err != nil {
		return err
	}
	if err := s.repo.Cancel(ctx, id); err != nil {
		return err
	}
	if err := s.queue.Cancel(ctx, id); err != nil {
		return err
	}
	s.publish(ctx, "task.cancelled", map[string]string{"id": id})
	return nil
}

func (s *TaskService) Attempts(ctx context.Context, id string) ([]task.Attempt, error) {
	if err := validateTaskID(id); err != nil {
		return nil, err
	}
	return s.repo.Attempts(ctx, id)
}

func (s *TaskService) DeadLetters(ctx context.Context, limit int64) ([]task.Task, error) {
	ids, err := s.queue.DeadLetters(ctx, limit)
	if err != nil {
		return nil, err
	}
	return s.repo.ListByIDs(ctx, ids)
}

func (s *TaskService) RequeueDeadLetter(ctx context.Context, id string) error {
	if err := validateTaskID(id); err != nil {
		return err
	}
	item, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return err
	}
	if item.Status != task.StatusDeadLettered && item.Status != task.StatusFailed {
		return fmt.Errorf("%w: task is not in a dead-letter state", ErrConflict)
	}
	if err := s.queue.RequeueDeadLetter(ctx, id); err != nil {
		return err
	}
	if err := s.repo.MarkRetrying(ctx, id, "manually requeued"); err != nil {
		return err
	}
	s.publish(ctx, "task.requeued", map[string]string{"id": id})
	return nil
}

func (s *TaskService) QueueStats(ctx context.Context) (task.QueueStats, error) {
	return s.queue.Stats(ctx)
}

func validateCreate(req task.CreateRequest) error {
	if !validTaskType(req.Type) {
		return fmt.Errorf("%w: unsupported task type", ErrInvalidInput)
	}
	if req.Payload == nil {
		return fmt.Errorf("%w: payload is required", ErrInvalidInput)
	}
	if req.MaxAttempts < 0 || req.MaxAttempts > 20 || req.DelaySeconds < 0 || req.DelaySeconds > 604800 || req.TimeoutSeconds < 0 || req.TimeoutSeconds > 3600 {
		return fmt.Errorf("%w: numeric values are outside allowed bounds", ErrInvalidInput)
	}
	return nil
}

func validTaskType(taskType string) bool {
	switch strings.TrimSpace(taskType) {
	case "example.email", "example.webhook", "example.report", "example.fail_once", "example.always_fail":
		return true
	default:
		return false
	}
}

func (s *TaskService) publish(ctx context.Context, subject string, value any) {
	if s.bus == nil {
		return
	}
	payload, err := json.Marshal(value)
	if err != nil {
		return
	}
	_ = s.bus.Publish(ctx, subject, payload)
}

func validateTaskID(id string) error {
	if strings.TrimSpace(id) == "" {
		return fmt.Errorf("%w: task id is required", ErrInvalidInput)
	}
	if !uuidPattern.MatchString(id) {
		return fmt.Errorf("%w: task id must be a UUID", ErrInvalidInput)
	}
	return nil
}

func newID() (string, error) {
	var bytes [16]byte
	if _, err := rand.Read(bytes[:]); err != nil {
		return "", fmt.Errorf("generate task id: %w", err)
	}
	bytes[6] = (bytes[6] & 0x0f) | 0x40
	bytes[8] = (bytes[8] & 0x3f) | 0x80
	encoded := hex.EncodeToString(bytes[:])
	return fmt.Sprintf("%s-%s-%s-%s-%s", encoded[0:8], encoded[8:12], encoded[12:16], encoded[16:20], encoded[20:32]), nil
}
