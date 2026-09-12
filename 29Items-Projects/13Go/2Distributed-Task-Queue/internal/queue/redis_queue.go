package queue

import (
	"context"
	"errors"
	"time"

	"github.com/example/distributed-task-queue/pkg/task"
	"github.com/redis/go-redis/v9"
)

var ErrEmpty = errors.New("queue is empty")

type Queue interface {
	Enqueue(ctx context.Context, taskID string, runAt time.Time) error
	Reserve(ctx context.Context) (string, error)
	Ack(ctx context.Context, taskID string) error
	Retry(ctx context.Context, taskID string, runAt time.Time) error
	DeadLetter(ctx context.Context, taskID string) error
	Cancel(ctx context.Context, taskID string) error
	RequeueDeadLetter(ctx context.Context, taskID string) error
	DeadLetters(ctx context.Context, limit int64) ([]string, error)
	PromoteDue(ctx context.Context, now time.Time, limit int64) (int64, error)
	Stats(ctx context.Context) (task.QueueStats, error)
}

type RedisQueue struct {
	client *redis.Client
	name   string
}

func NewRedisQueue(client *redis.Client, name string) *RedisQueue {
	return &RedisQueue{client: client, name: name}
}

func NewRedisClient(addr, password string) *redis.Client {
	return redis.NewClient(&redis.Options{
		Addr:     addr,
		Password: password,
	})
}

func (q *RedisQueue) Enqueue(ctx context.Context, taskID string, runAt time.Time) error {
	if runAt.IsZero() || !runAt.After(time.Now().UTC()) {
		return q.client.LPush(ctx, q.pendingKey(), taskID).Err()
	}
	return q.client.ZAdd(ctx, q.retryKey(), redis.Z{Score: float64(runAt.Unix()), Member: taskID}).Err()
}

func (q *RedisQueue) Reserve(ctx context.Context) (string, error) {
	result, err := reserveScript.Run(ctx, q.client, []string{q.pendingKey(), q.reservedKey()}).Text()
	if errors.Is(err, redis.Nil) || result == "" {
		return "", ErrEmpty
	}
	if err != nil {
		return "", err
	}
	return result, nil
}

func (q *RedisQueue) Ack(ctx context.Context, taskID string) error {
	return q.client.LRem(ctx, q.reservedKey(), 1, taskID).Err()
}

func (q *RedisQueue) Retry(ctx context.Context, taskID string, runAt time.Time) error {
	_, err := retryScript.Run(ctx, q.client, []string{q.reservedKey(), q.retryKey()}, taskID, runAt.Unix()).Result()
	return err
}

func (q *RedisQueue) DeadLetter(ctx context.Context, taskID string) error {
	_, err := moveListScript.Run(ctx, q.client, []string{q.reservedKey(), q.deadKey()}, taskID).Result()
	return err
}

func (q *RedisQueue) Cancel(ctx context.Context, taskID string) error {
	pipe := q.client.TxPipeline()
	pipe.LRem(ctx, q.pendingKey(), 0, taskID)
	pipe.LRem(ctx, q.reservedKey(), 0, taskID)
	pipe.ZRem(ctx, q.retryKey(), taskID)
	_, err := pipe.Exec(ctx)
	return err
}

func (q *RedisQueue) RequeueDeadLetter(ctx context.Context, taskID string) error {
	_, err := moveListScript.Run(ctx, q.client, []string{q.deadKey(), q.pendingKey()}, taskID).Result()
	return err
}

func (q *RedisQueue) DeadLetters(ctx context.Context, limit int64) ([]string, error) {
	if limit <= 0 || limit > 200 {
		limit = 50
	}
	return q.client.LRange(ctx, q.deadKey(), 0, limit-1).Result()
}

func (q *RedisQueue) PromoteDue(ctx context.Context, now time.Time, limit int64) (int64, error) {
	if limit <= 0 {
		limit = 100
	}
	result, err := promoteDueScript.Run(ctx, q.client, []string{q.retryKey(), q.pendingKey()}, now.Unix(), limit).Int64()
	if errors.Is(err, redis.Nil) {
		return 0, nil
	}
	return result, err
}

func (q *RedisQueue) Stats(ctx context.Context) (task.QueueStats, error) {
	pipe := q.client.TxPipeline()
	pending := pipe.LLen(ctx, q.pendingKey())
	reserved := pipe.LLen(ctx, q.reservedKey())
	retry := pipe.ZCard(ctx, q.retryKey())
	dead := pipe.LLen(ctx, q.deadKey())
	if _, err := pipe.Exec(ctx); err != nil {
		return task.QueueStats{}, err
	}
	return task.QueueStats{
		Pending:     pending.Val(),
		Reserved:    reserved.Val(),
		Retry:       retry.Val(),
		DeadLetters: dead.Val(),
	}, nil
}

func (q *RedisQueue) pendingKey() string {
	return q.name + ":pending"
}

func (q *RedisQueue) reservedKey() string {
	return q.name + ":reserved"
}

func (q *RedisQueue) retryKey() string {
	return q.name + ":retry"
}

func (q *RedisQueue) deadKey() string {
	return q.name + ":dead"
}

var reserveScript = redis.NewScript(`
local task_id = redis.call("RPOP", KEYS[1])
if not task_id then
  return nil
end
redis.call("LPUSH", KEYS[2], task_id)
return task_id
`)

var retryScript = redis.NewScript(`
redis.call("LREM", KEYS[1], 0, ARGV[1])
redis.call("ZADD", KEYS[2], ARGV[2], ARGV[1])
return 1
`)

var moveListScript = redis.NewScript(`
redis.call("LREM", KEYS[1], 0, ARGV[1])
redis.call("LPUSH", KEYS[2], ARGV[1])
return 1
`)

var promoteDueScript = redis.NewScript(`
local items = redis.call("ZRANGEBYSCORE", KEYS[1], "-inf", ARGV[1], "LIMIT", 0, ARGV[2])
if #items == 0 then
  return 0
end
for _, item in ipairs(items) do
  redis.call("ZREM", KEYS[1], item)
  redis.call("LPUSH", KEYS[2], item)
end
return #items
`)
