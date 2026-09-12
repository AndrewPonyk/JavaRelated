package integration_test

import (
	"context"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	redisclient "github.com/redis/go-redis/v9"
	"github.com/stretchr/testify/require"

	"github.com/example/url-shortener-service/internal/models"
	"github.com/example/url-shortener-service/internal/service"
	"github.com/example/url-shortener-service/internal/store/postgres"
	rediscache "github.com/example/url-shortener-service/internal/store/redis"
)

func TestPostgresRedisURLFlow(t *testing.T) {
	databaseURL := os.Getenv("DATABASE_URL")
	redisAddr := os.Getenv("REDIS_ADDR")
	if databaseURL == "" || redisAddr == "" {
		t.Skip("DATABASE_URL and REDIS_ADDR are required for integration tests")
	}

	ctx := context.Background()
	db, err := pgxpool.New(ctx, databaseURL)
	require.NoError(t, err)
	defer db.Close()

	require.NoError(t, applyMigrations(ctx, db))
	_, err = db.Exec(ctx, "TRUNCATE url_click_rollups_hourly, url_clicks, urls, api_keys, abuse_reports, blocked_domains RESTART IDENTITY CASCADE")
	require.NoError(t, err)

	rdb := redisclient.NewClient(&redisclient.Options{Addr: redisAddr, Password: os.Getenv("REDIS_PASSWORD")})
	defer func() { _ = rdb.Close() }()
	require.NoError(t, rdb.FlushDB(ctx).Err())

	repo := postgres.NewURLRepository(db)
	cache := rediscache.NewURLCache(rdb, time.Minute)
	svc := service.NewURLService(repo, cache, "http://short.test")

	created, err := svc.Create(ctx, models.CreateURLRequest{
		OriginalURL: "https://example.com/integration",
		CustomCode:  "itest",
	})
	require.NoError(t, err)
	require.Equal(t, "http://short.test/itest", created.ShortURL)

	target, err := svc.Resolve(ctx, "itest", service.ClickMetadata{Referrer: "https://ref.example", UserAgent: "integration-test", Country: "UA", IP: "127.0.0.1"})
	require.NoError(t, err)
	require.Equal(t, "https://example.com/integration", target)

	summary, err := svc.Analytics(ctx, "itest")
	require.NoError(t, err)
	require.Equal(t, int64(1), summary.TotalClicks)
	require.Equal(t, int64(1), summary.ByCountry["UA"])

	require.NoError(t, svc.Delete(ctx, "itest"))
	_, err = svc.Get(ctx, "itest")
	require.ErrorIs(t, err, service.ErrNotFound)
}

func applyMigrations(ctx context.Context, db *pgxpool.Pool) error {
	files, err := filepath.Glob(filepath.Join("..", "..", "migrations", "*.up.sql"))
	if err != nil {
		return err
	}
	sort.Strings(files)

	for _, file := range files {
		content, err := os.ReadFile(file)
		if err != nil {
			return err
		}
		for _, statement := range strings.Split(string(content), ";") {
			statement = strings.TrimSpace(statement)
			if statement == "" {
				continue
			}
			if _, err := db.Exec(ctx, statement); err != nil {
				return err
			}
		}
	}
	return nil
}
