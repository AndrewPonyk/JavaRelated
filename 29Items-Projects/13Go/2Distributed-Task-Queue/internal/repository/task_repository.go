package repository

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/example/distributed-task-queue/pkg/task"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

var (
	ErrNotFound      = errors.New("not found")
	ErrStateConflict = errors.New("state conflict")
)

type ListFilter struct {
	Status task.Status
	Limit  int
}

type TaskRepository interface {
	Create(ctx context.Context, item task.Task, timeoutSeconds int) (task.Task, bool, error)
	GetByID(ctx context.Context, id string) (task.Task, error)
	ListByIDs(ctx context.Context, ids []string) ([]task.Task, error)
	List(ctx context.Context, filter ListFilter) ([]task.Task, error)
	UpdateQueued(ctx context.Context, id string, req task.UpdateRequest) (task.Task, error)
	Delete(ctx context.Context, id string) error
	MarkRunning(ctx context.Context, id string) (task.Task, int, error)
	MarkCompleted(ctx context.Context, id string, result map[string]any) error
	MarkRetrying(ctx context.Context, id string, errText string) error
	MarkFailed(ctx context.Context, id string, status task.Status, errText string) error
	Cancel(ctx context.Context, id string) error
	Attempts(ctx context.Context, id string) ([]task.Attempt, error)
}

type PostgresTaskRepository struct {
	pool *pgxpool.Pool
}

func NewPostgresTaskRepository(pool *pgxpool.Pool) *PostgresTaskRepository {
	return &PostgresTaskRepository{pool: pool}
}

func (r *PostgresTaskRepository) Create(ctx context.Context, item task.Task, timeoutSeconds int) (task.Task, bool, error) {
	payload, err := json.Marshal(item.Payload)
	if err != nil {
		return task.Task{}, false, err
	}
	if timeoutSeconds <= 0 {
		timeoutSeconds = 30
	}

	row := r.pool.QueryRow(ctx, `
		WITH inserted AS (
			INSERT INTO tasks (id, task_type, payload, status, max_attempts, idempotency_key, run_at, timeout_seconds)
			VALUES ($1, $2, $3, $4, $5, NULLIF($6, ''), $7, $8)
			ON CONFLICT (idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING
			RETURNING *, true AS created_new
		)
		SELECT id, task_type, payload, status, attempts, max_attempts,
		       COALESCE(idempotency_key, ''), COALESCE(result, '{}'::jsonb),
		       COALESCE(error, ''), run_at, timeout_seconds, created_at, updated_at, created_new
		FROM inserted
		UNION ALL
		SELECT id, task_type, payload, status, attempts, max_attempts,
		       COALESCE(idempotency_key, ''), COALESCE(result, '{}'::jsonb),
		       COALESCE(error, ''), run_at, timeout_seconds, created_at, updated_at, false AS created_new
		FROM tasks
		WHERE idempotency_key = NULLIF($6, '')
		LIMIT 1
	`, item.ID, item.Type, payload, string(item.Status), item.MaxAttempts, item.IdempotencyKey, item.RunAt, timeoutSeconds)

	created, err := scanTaskWithCreated(row)
	return created.task, created.createdNew, err
}

func (r *PostgresTaskRepository) GetByID(ctx context.Context, id string) (task.Task, error) {
	row := r.pool.QueryRow(ctx, baseTaskSelect()+` WHERE id = $1`, id)
	return scanTask(row)
}

func (r *PostgresTaskRepository) ListByIDs(ctx context.Context, ids []string) ([]task.Task, error) {
	if len(ids) == 0 {
		return []task.Task{}, nil
	}

	args := make([]any, 0, len(ids))
	bindVars := make([]string, 0, len(ids))
	for index, id := range ids {
		args = append(args, id)
		bindVars = append(bindVars, fmt.Sprintf("$%d", index+1))
	}

	rows, err := r.pool.Query(ctx, baseTaskSelect()+` WHERE id IN (`+strings.Join(bindVars, ",")+`)`, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	items := make([]task.Task, 0, len(ids))
	for rows.Next() {
		item, err := scanTask(rows)
		if err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	return items, rows.Err()
}

func (r *PostgresTaskRepository) List(ctx context.Context, filter ListFilter) ([]task.Task, error) {
	if filter.Limit <= 0 || filter.Limit > 200 {
		filter.Limit = 50
	}

	var rows pgx.Rows
	var err error
	if filter.Status != "" {
		rows, err = r.pool.Query(ctx, baseTaskSelect()+` WHERE status = $1 ORDER BY created_at DESC LIMIT $2`, string(filter.Status), filter.Limit)
	} else {
		rows, err = r.pool.Query(ctx, baseTaskSelect()+` ORDER BY created_at DESC LIMIT $1`, filter.Limit)
	}
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	items := make([]task.Task, 0)
	for rows.Next() {
		item, err := scanTask(rows)
		if err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	return items, rows.Err()
}

func (r *PostgresTaskRepository) UpdateQueued(ctx context.Context, id string, req task.UpdateRequest) (task.Task, error) {
	current, err := r.GetByID(ctx, id)
	if err != nil {
		return task.Task{}, err
	}
	if current.Status != task.StatusPending && current.Status != task.StatusRetrying {
		return task.Task{}, errors.New("only pending or retrying tasks can be updated")
	}
	if req.Type != "" {
		current.Type = req.Type
	}
	if req.Payload != nil {
		current.Payload = req.Payload
	}
	if req.MaxAttempts > 0 {
		current.MaxAttempts = req.MaxAttempts
	}
	if req.DelaySeconds > 0 {
		current.RunAt = time.Now().UTC().Add(time.Duration(req.DelaySeconds) * time.Second)
	}
	if req.TimeoutSeconds > 0 {
		current.TimeoutSeconds = req.TimeoutSeconds
	}

	payload, err := json.Marshal(current.Payload)
	if err != nil {
		return task.Task{}, err
	}

	row := r.pool.QueryRow(ctx, `
		UPDATE tasks
		SET task_type = $2,
		    payload = $3,
		    max_attempts = $4,
		    run_at = $5,
		    timeout_seconds = $6,
		    updated_at = now()
		WHERE id = $1
		  AND status IN ('pending', 'retrying')
		RETURNING id, task_type, payload, status, attempts, max_attempts,
		          COALESCE(idempotency_key, ''), COALESCE(result, '{}'::jsonb),
		          COALESCE(error, ''), run_at, timeout_seconds, created_at, updated_at
	`, id, current.Type, payload, current.MaxAttempts, current.RunAt, current.TimeoutSeconds)
	return scanTask(row)
}

func (r *PostgresTaskRepository) Delete(ctx context.Context, id string) error {
	tag, err := r.pool.Exec(ctx, `DELETE FROM tasks WHERE id = $1`, id)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

func (r *PostgresTaskRepository) MarkRunning(ctx context.Context, id string) (task.Task, int, error) {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return task.Task{}, 0, err
	}
	defer tx.Rollback(ctx)

	row := tx.QueryRow(ctx, `
		UPDATE tasks
		SET status = 'running',
		    attempts = attempts + 1,
		    error = NULL,
		    updated_at = now()
		WHERE id = $1
		  AND status IN ('pending', 'retrying')
		RETURNING id, task_type, payload, status, attempts, max_attempts,
		          COALESCE(idempotency_key, ''), COALESCE(result, '{}'::jsonb),
		          COALESCE(error, ''), run_at, timeout_seconds, created_at, updated_at
	`, id)
	item, err := scanTask(row)
	if err != nil {
		return task.Task{}, 0, err
	}

	var attemptID int64
	err = tx.QueryRow(ctx, `
		INSERT INTO task_attempts (task_id, attempt_number, status)
		VALUES ($1, $2, $3)
		RETURNING id
	`, id, item.Attempts, string(task.StatusRunning)).Scan(&attemptID)
	if err != nil {
		return task.Task{}, 0, err
	}

	if err := tx.Commit(ctx); err != nil {
		return task.Task{}, 0, err
	}
	return item, int(attemptID), nil
}

func (r *PostgresTaskRepository) MarkCompleted(ctx context.Context, id string, result map[string]any) error {
	return r.finishAttempt(ctx, id, task.StatusCompleted, result, "")
}

func (r *PostgresTaskRepository) MarkRetrying(ctx context.Context, id string, errText string) error {
	return r.finishAttempt(ctx, id, task.StatusRetrying, nil, errText)
}

func (r *PostgresTaskRepository) MarkFailed(ctx context.Context, id string, status task.Status, errText string) error {
	if status != task.StatusFailed && status != task.StatusDeadLettered {
		return errors.New("invalid failure status")
	}
	return r.finishAttempt(ctx, id, status, nil, errText)
}

func (r *PostgresTaskRepository) Cancel(ctx context.Context, id string) error {
	return r.finishAttempt(ctx, id, task.StatusCancelled, nil, "cancelled")
}

func (r *PostgresTaskRepository) Attempts(ctx context.Context, id string) ([]task.Attempt, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT id, task_id, attempt_number, status, COALESCE(error, ''), started_at, finished_at
		FROM task_attempts
		WHERE task_id = $1
		ORDER BY attempt_number ASC
	`, id)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	attempts := make([]task.Attempt, 0)
	for rows.Next() {
		var item task.Attempt
		var status string
		if err := rows.Scan(&item.ID, &item.TaskID, &item.AttemptNumber, &status, &item.Error, &item.StartedAt, &item.FinishedAt); err != nil {
			return nil, err
		}
		item.Status = task.Status(status)
		attempts = append(attempts, item)
	}
	return attempts, rows.Err()
}

func (r *PostgresTaskRepository) finishAttempt(ctx context.Context, id string, status task.Status, result map[string]any, errText string) error {
	resultJSON, err := json.Marshal(result)
	if err != nil {
		return err
	}
	if result == nil {
		resultJSON = nil
	}

	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	statePredicate := `AND status = 'running'`
	if status == task.StatusCancelled {
		statePredicate = `AND status NOT IN ('completed', 'failed', 'cancelled', 'dead_lettered')`
	}
	if status == task.StatusRetrying {
		statePredicate = `AND status IN ('running', 'failed', 'dead_lettered')`
	}

	tag, err := tx.Exec(ctx, `
		UPDATE tasks
		SET status = $2,
		    result = COALESCE($3::jsonb, result),
		    error = NULLIF($4, ''),
		    updated_at = now()
		WHERE id = $1
		`+statePredicate, id, string(status), nullableJSON(resultJSON), errText)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		if _, err := r.GetByID(ctx, id); err != nil {
			return err
		}
		return ErrStateConflict
	}

	_, err = tx.Exec(ctx, `
		UPDATE task_attempts
		SET status = $2,
		    error = NULLIF($3, ''),
		    finished_at = now()
		WHERE id = (
			SELECT id
			FROM task_attempts
			WHERE task_id = $1
			ORDER BY attempt_number DESC
			LIMIT 1
		)
	`, id, string(status), errText)
	if err != nil {
		return err
	}

	return tx.Commit(ctx)
}

func baseTaskSelect() string {
	return `
		SELECT id, task_type, payload, status, attempts, max_attempts,
		       COALESCE(idempotency_key, ''), COALESCE(result, '{}'::jsonb),
		       COALESCE(error, ''), run_at, timeout_seconds, created_at, updated_at
		FROM tasks`
}

type scanner interface {
	Scan(dest ...any) error
}

type createdTask struct {
	task       task.Task
	createdNew bool
}

func scanTaskWithCreated(row scanner) (createdTask, error) {
	var item task.Task
	var payload []byte
	var result []byte
	var status string
	var createdNew bool
	err := row.Scan(
		&item.ID,
		&item.Type,
		&payload,
		&status,
		&item.Attempts,
		&item.MaxAttempts,
		&item.IdempotencyKey,
		&result,
		&item.Error,
		&item.RunAt,
		&item.TimeoutSeconds,
		&item.CreatedAt,
		&item.UpdatedAt,
		&createdNew,
	)
	if err != nil {
		return createdTask{}, normalizeNotFound(err)
	}
	if err := decodeJSON(payload, &item.Payload); err != nil {
		return createdTask{}, err
	}
	if err := decodeJSON(result, &item.Result); err != nil {
		return createdTask{}, err
	}
	item.Status = task.Status(status)
	return createdTask{task: item, createdNew: createdNew}, nil
}

func scanTask(row scanner) (task.Task, error) {
	var item task.Task
	var payload []byte
	var result []byte
	var status string

	err := row.Scan(
		&item.ID,
		&item.Type,
		&payload,
		&status,
		&item.Attempts,
		&item.MaxAttempts,
		&item.IdempotencyKey,
		&result,
		&item.Error,
		&item.RunAt,
		&item.TimeoutSeconds,
		&item.CreatedAt,
		&item.UpdatedAt,
	)
	if err != nil {
		return task.Task{}, normalizeNotFound(err)
	}
	if err := decodeJSON(payload, &item.Payload); err != nil {
		return task.Task{}, err
	}
	if err := decodeJSON(result, &item.Result); err != nil {
		return task.Task{}, err
	}
	item.Status = task.Status(status)
	return item, nil
}

func normalizeNotFound(err error) error {
	if errors.Is(err, pgx.ErrNoRows) {
		return ErrNotFound
	}
	return err
}

func decodeJSON(data []byte, dest *map[string]any) error {
	if len(data) == 0 {
		*dest = map[string]any{}
		return nil
	}
	return json.Unmarshal(data, dest)
}

func nullableJSON(data []byte) any {
	if len(data) == 0 {
		return nil
	}
	return string(data)
}
