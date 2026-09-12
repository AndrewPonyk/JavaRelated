package redis

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	redisclient "github.com/redis/go-redis/v9"

	"github.com/example/url-shortener-service/internal/models"
	"github.com/example/url-shortener-service/internal/service"
)

type URLCache struct {
	client *redisclient.Client
	ttl    time.Duration
}

func NewURLCache(client *redisclient.Client, ttl time.Duration) *URLCache {
	return &URLCache{client: client, ttl: ttl}
}

func (c *URLCache) Get(ctx context.Context, code string) (models.URL, error) {
	raw, err := c.client.Get(ctx, urlKey(code)).Result()
	if errors.Is(err, redisclient.Nil) {
		return models.URL{}, service.ErrNotFound
	}
	if err != nil {
		return models.URL{}, fmt.Errorf("redis get url: %w", err)
	}

	var item models.URL
	if err := json.Unmarshal([]byte(raw), &item); err != nil {
		return models.URL{}, fmt.Errorf("decode cached url: %w", err)
	}
	return item, nil
}

func (c *URLCache) Set(ctx context.Context, item models.URL) error {
	payload, err := json.Marshal(item)
	if err != nil {
		return fmt.Errorf("encode cached url: %w", err)
	}
	if err := c.client.Set(ctx, urlKey(item.ShortCode), payload, c.ttl).Err(); err != nil {
		return fmt.Errorf("redis set url: %w", err)
	}
	return nil
}

func (c *URLCache) Delete(ctx context.Context, code string) error {
	if err := c.client.Del(ctx, urlKey(code)).Err(); err != nil {
		return fmt.Errorf("redis delete url: %w", err)
	}
	return nil
}

func (c *URLCache) RecordClick(ctx context.Context, code string, metadata service.ClickMetadata) error {
	when := metadata.At
	if when.IsZero() {
		when = time.Now().UTC()
	}

	bucket := when.UTC().Format("200601021504")
	pipe := c.client.Pipeline()
	pipe.Incr(ctx, fmt.Sprintf("series:clicks:%s:%s", code, bucket))
	pipe.Incr(ctx, fmt.Sprintf("series:clicks:%s:total", code))
	pipe.Expire(ctx, fmt.Sprintf("series:clicks:%s:%s", code, bucket), 48*time.Hour)
	pipe.Expire(ctx, fmt.Sprintf("series:clicks:%s:total", code), 48*time.Hour)

	if metadata.Referrer != "" {
		pipe.HIncrBy(ctx, fmt.Sprintf("series:referrer:%s", code), normalizeDimension(metadata.Referrer), 1)
		pipe.Expire(ctx, fmt.Sprintf("series:referrer:%s", code), 48*time.Hour)
	}
	if metadata.Country != "" {
		pipe.HIncrBy(ctx, fmt.Sprintf("series:country:%s", code), normalizeDimension(metadata.Country), 1)
		pipe.Expire(ctx, fmt.Sprintf("series:country:%s", code), 48*time.Hour)
	}
	if metadata.UserAgent != "" {
		pipe.HIncrBy(ctx, fmt.Sprintf("series:user_agent:%s", code), normalizeDimension(metadata.UserAgent), 1)
		pipe.Expire(ctx, fmt.Sprintf("series:user_agent:%s", code), 48*time.Hour)
	}

	_, err := pipe.Exec(ctx)
	if err != nil {
		return fmt.Errorf("redis record click: %w", err)
	}
	return nil
}

func urlKey(code string) string {
	return "url:" + code
}

func normalizeDimension(value string) string {
	value = strings.TrimSpace(strings.ToLower(value))
	if value == "" {
		return "unknown"
	}
	if len(value) > 256 {
		return value[:256]
	}
	return value
}
