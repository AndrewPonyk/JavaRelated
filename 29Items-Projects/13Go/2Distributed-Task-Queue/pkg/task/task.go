package task

import "time"

type Status string

const (
	StatusPending      Status = "pending"
	StatusRunning      Status = "running"
	StatusCompleted    Status = "completed"
	StatusFailed       Status = "failed"
	StatusCancelled    Status = "cancelled"
	StatusRetrying     Status = "retrying"
	StatusDeadLettered Status = "dead_lettered"
)

type Task struct {
	ID             string         `json:"id"`
	Type           string         `json:"type"`
	Payload        map[string]any `json:"payload"`
	Status         Status         `json:"status"`
	Attempts       int            `json:"attempts"`
	MaxAttempts    int            `json:"max_attempts"`
	IdempotencyKey string         `json:"idempotency_key,omitempty"`
	Result         map[string]any `json:"result,omitempty"`
	Error          string         `json:"error,omitempty"`
	RunAt          time.Time      `json:"run_at"`
	TimeoutSeconds int            `json:"timeout_seconds"`
	CreatedAt      time.Time      `json:"created_at"`
	UpdatedAt      time.Time      `json:"updated_at"`
}

type CreateRequest struct {
	Type           string         `json:"type"`
	Payload        map[string]any `json:"payload"`
	MaxAttempts    int            `json:"max_attempts"`
	IdempotencyKey string         `json:"idempotency_key,omitempty"`
	DelaySeconds   int            `json:"delay_seconds,omitempty"`
	TimeoutSeconds int            `json:"timeout_seconds,omitempty"`
}

type CreateResponse struct {
	ID     string `json:"id"`
	Status Status `json:"status"`
}

type UpdateRequest struct {
	Type           string         `json:"type,omitempty"`
	Payload        map[string]any `json:"payload,omitempty"`
	MaxAttempts    int            `json:"max_attempts,omitempty"`
	DelaySeconds   int            `json:"delay_seconds,omitempty"`
	TimeoutSeconds int            `json:"timeout_seconds,omitempty"`
}

type Attempt struct {
	ID            int64      `json:"id"`
	TaskID        string     `json:"task_id"`
	AttemptNumber int        `json:"attempt_number"`
	Status        Status     `json:"status"`
	Error         string     `json:"error,omitempty"`
	StartedAt     time.Time  `json:"started_at"`
	FinishedAt    *time.Time `json:"finished_at,omitempty"`
}

type QueueStats struct {
	Pending     int64 `json:"pending"`
	Reserved    int64 `json:"reserved"`
	Retry       int64 `json:"retry"`
	DeadLetters int64 `json:"dead_letters"`
}

func IsTerminal(status Status) bool {
	return status == StatusCompleted || status == StatusFailed || status == StatusCancelled || status == StatusDeadLettered
}

func IsValidStatus(status Status) bool {
	switch status {
	case StatusPending, StatusRunning, StatusCompleted, StatusFailed, StatusCancelled, StatusRetrying, StatusDeadLettered:
		return true
	default:
		return false
	}
}
