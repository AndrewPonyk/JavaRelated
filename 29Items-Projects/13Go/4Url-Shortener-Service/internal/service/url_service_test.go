package service_test

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/require"

	"github.com/example/url-shortener-service/internal/models"
	"github.com/example/url-shortener-service/internal/service"
)

func TestURLServiceCreateValidatesScheme(t *testing.T) {
	svc := service.NewURLService(newFakeRepo(), newFakeCache(), "http://short.test")

	_, err := svc.Create(context.Background(), models.CreateURLRequest{
		OriginalURL: "ftp://example.com/file",
	})

	require.ErrorIs(t, err, service.ErrValidation)
}

func TestURLServiceCreateAndResolve(t *testing.T) {
	svc := service.NewURLService(newFakeRepo(), newFakeCache(), "http://short.test")

	created, err := svc.Create(context.Background(), models.CreateURLRequest{
		OriginalURL: "https://example.com/article",
		CustomCode:  "article",
	})
	require.NoError(t, err)
	require.Equal(t, "http://short.test/article", created.ShortURL)

	target, err := svc.Resolve(context.Background(), "article", service.ClickMetadata{})
	require.NoError(t, err)
	require.Equal(t, "https://example.com/article", target)
}

func TestURLServiceRejectsReservedShortCode(t *testing.T) {
	svc := service.NewURLService(newFakeRepo(), newFakeCache(), "http://short.test")

	_, err := svc.Create(context.Background(), models.CreateURLRequest{
		OriginalURL: "https://example.com",
		CustomCode:  "api",
	})

	require.ErrorIs(t, err, service.ErrValidation)
}

func TestURLServiceAnalytics(t *testing.T) {
	svc := service.NewURLService(newFakeRepo(), newFakeCache(), "http://short.test")

	_, err := svc.Create(context.Background(), models.CreateURLRequest{
		OriginalURL: "https://example.com",
		CustomCode:  "abc123",
	})
	require.NoError(t, err)

	_, err = svc.Resolve(context.Background(), "abc123", service.ClickMetadata{Referrer: "https://ref.example", UserAgent: "test-agent"})
	require.NoError(t, err)

	summary, err := svc.Analytics(context.Background(), "abc123")
	require.NoError(t, err)
	require.Equal(t, int64(1), summary.TotalClicks)
	require.Equal(t, int64(1), summary.ByReferrer["https://ref.example"])
}

type fakeRepo struct {
	items  map[string]models.URL
	clicks map[string][]service.ClickMetadata
}

func newFakeRepo() *fakeRepo {
	return &fakeRepo{items: map[string]models.URL{}, clicks: map[string][]service.ClickMetadata{}}
}

func (r *fakeRepo) Create(_ context.Context, item models.URL) (models.URL, error) {
	if _, exists := r.items[item.ShortCode]; exists {
		return models.URL{}, service.ErrConflict
	}
	item.ID = "test-id"
	now := time.Now().UTC()
	item.CreatedAt = now
	item.UpdatedAt = now
	r.items[item.ShortCode] = item
	return item, nil
}

func (r *fakeRepo) GetByCode(_ context.Context, code string) (models.URL, error) {
	item, exists := r.items[code]
	if !exists {
		return models.URL{}, service.ErrNotFound
	}
	return item, nil
}

func (r *fakeRepo) List(_ context.Context, _ int) ([]models.URL, error) {
	items := make([]models.URL, 0, len(r.items))
	for _, item := range r.items {
		items = append(items, item)
	}
	return items, nil
}

func (r *fakeRepo) Update(_ context.Context, code string, req models.UpdateURLRequest) (models.URL, error) {
	item, exists := r.items[code]
	if !exists {
		return models.URL{}, service.ErrNotFound
	}
	if req.OriginalURL != "" {
		item.OriginalURL = req.OriginalURL
	}
	if req.Title != "" {
		item.Title = req.Title
	}
	item.ExpiresAt = req.ExpiresAt
	item.UpdatedAt = time.Now().UTC()
	r.items[code] = item
	return item, nil
}

func (r *fakeRepo) Delete(_ context.Context, code string) error {
	if _, exists := r.items[code]; !exists {
		return service.ErrNotFound
	}
	delete(r.items, code)
	return nil
}

func (r *fakeRepo) RecordClick(_ context.Context, code string, metadata service.ClickMetadata) error {
	if _, exists := r.items[code]; !exists {
		return service.ErrNotFound
	}
	r.clicks[code] = append(r.clicks[code], metadata)
	return nil
}

func (r *fakeRepo) Analytics(_ context.Context, code string) (models.AnalyticsSummary, error) {
	clicks, exists := r.clicks[code]
	if !exists {
		clicks = []service.ClickMetadata{}
	}

	summary := models.AnalyticsSummary{
		ShortCode:   code,
		TotalClicks: int64(len(clicks)),
		ByReferrer:  map[string]int64{},
		ByCountry:   map[string]int64{},
		ByUserAgent: map[string]int64{},
		ByBucket:    []models.AnalyticsBucket{},
	}
	for _, click := range clicks {
		if click.Referrer != "" {
			summary.ByReferrer[click.Referrer]++
		}
		if click.Country != "" {
			summary.ByCountry[click.Country]++
		}
		if click.UserAgent != "" {
			summary.ByUserAgent[click.UserAgent]++
		}
	}
	return summary, nil
}

type fakeCache struct {
	items map[string]models.URL
}

func newFakeCache() *fakeCache {
	return &fakeCache{items: map[string]models.URL{}}
}

func (c *fakeCache) Get(_ context.Context, code string) (models.URL, error) {
	item, exists := c.items[code]
	if !exists {
		return models.URL{}, service.ErrNotFound
	}
	return item, nil
}

func (c *fakeCache) Set(_ context.Context, item models.URL) error {
	c.items[item.ShortCode] = item
	return nil
}

func (c *fakeCache) Delete(_ context.Context, code string) error {
	delete(c.items, code)
	return nil
}

func (c *fakeCache) RecordClick(_ context.Context, _ string, _ service.ClickMetadata) error {
	return nil
}
