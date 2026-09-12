package postgres

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/example/url-shortener-service/internal/models"
	"github.com/example/url-shortener-service/internal/service"
)

type URLRepository struct {
	db *pgxpool.Pool
}

func NewURLRepository(db *pgxpool.Pool) *URLRepository {
	return &URLRepository{db: db}
}

func (r *URLRepository) Create(ctx context.Context, item models.URL) (models.URL, error) {
	const query = `
INSERT INTO urls (short_code, original_url, title, owner_key_id, expires_at)
VALUES ($1, $2, $3, $4, $5)
RETURNING id::text, short_code, original_url, title, owner_key_id::text, created_at, updated_at, expires_at, deleted_at`

	var created models.URL
	err := r.db.QueryRow(ctx, query, item.ShortCode, item.OriginalURL, item.Title, item.OwnerKeyID, item.ExpiresAt).Scan(
		&created.ID,
		&created.ShortCode,
		&created.OriginalURL,
		&created.Title,
		&created.OwnerKeyID,
		&created.CreatedAt,
		&created.UpdatedAt,
		&created.ExpiresAt,
		&created.DeletedAt,
	)
	if err != nil {
		return models.URL{}, mapPostgresError(err)
	}
	return created, nil
}

func (r *URLRepository) GetByCode(ctx context.Context, code string) (models.URL, error) {
	const query = `
SELECT id::text, short_code, original_url, title, owner_key_id::text, created_at, updated_at, expires_at, deleted_at
FROM urls
WHERE short_code = $1
  AND deleted_at IS NULL
  AND (expires_at IS NULL OR expires_at > now())`

	var item models.URL
	err := r.db.QueryRow(ctx, query, code).Scan(
		&item.ID,
		&item.ShortCode,
		&item.OriginalURL,
		&item.Title,
		&item.OwnerKeyID,
		&item.CreatedAt,
		&item.UpdatedAt,
		&item.ExpiresAt,
		&item.DeletedAt,
	)
	if err != nil {
		return models.URL{}, mapPostgresError(err)
	}
	return item, nil
}

func (r *URLRepository) List(ctx context.Context, limit int) ([]models.URL, error) {
	const query = `
SELECT id::text, short_code, original_url, title, owner_key_id::text, created_at, updated_at, expires_at, deleted_at
FROM urls
WHERE deleted_at IS NULL
ORDER BY created_at DESC
LIMIT $1`

	rows, err := r.db.Query(ctx, query, limit)
	if err != nil {
		return nil, mapPostgresError(err)
	}
	defer rows.Close()

	items := make([]models.URL, 0)
	for rows.Next() {
		var item models.URL
		if err := rows.Scan(
			&item.ID,
			&item.ShortCode,
			&item.OriginalURL,
			&item.Title,
			&item.OwnerKeyID,
			&item.CreatedAt,
			&item.UpdatedAt,
			&item.ExpiresAt,
			&item.DeletedAt,
		); err != nil {
			return nil, mapPostgresError(err)
		}
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, mapPostgresError(err)
	}
	return items, nil
}

func (r *URLRepository) Update(ctx context.Context, code string, req models.UpdateURLRequest) (models.URL, error) {
	const query = `
UPDATE urls
SET
  original_url = COALESCE(NULLIF($2, ''), original_url),
  title = COALESCE($3::text, title),
  expires_at = COALESCE($4::timestamptz, expires_at),
  updated_at = now()
WHERE short_code = $1
  AND deleted_at IS NULL
RETURNING id::text, short_code, original_url, title, owner_key_id::text, created_at, updated_at, expires_at, deleted_at`

	title := req.Title
	var titleArg *string
	if title != "" {
		titleArg = &title
	}

	var item models.URL
	err := r.db.QueryRow(ctx, query, code, req.OriginalURL, titleArg, req.ExpiresAt).Scan(
		&item.ID,
		&item.ShortCode,
		&item.OriginalURL,
		&item.Title,
		&item.OwnerKeyID,
		&item.CreatedAt,
		&item.UpdatedAt,
		&item.ExpiresAt,
		&item.DeletedAt,
	)
	if err != nil {
		return models.URL{}, mapPostgresError(err)
	}
	return item, nil
}

func (r *URLRepository) Delete(ctx context.Context, code string) error {
	const query = `
UPDATE urls
SET deleted_at = $2, updated_at = $2
WHERE short_code = $1
  AND deleted_at IS NULL`

	tag, err := r.db.Exec(ctx, query, code, time.Now().UTC())
	if err != nil {
		return mapPostgresError(err)
	}
	if tag.RowsAffected() == 0 {
		return service.ErrNotFound
	}
	return nil
}

func (r *URLRepository) RecordClick(ctx context.Context, code string, metadata service.ClickMetadata) error {
	const query = `
INSERT INTO url_clicks (url_id, short_code, clicked_at, referrer, user_agent, ip_hash, country_code)
SELECT id, short_code, $2, NULLIF($3, ''), NULLIF($4, ''), NULLIF($5, ''), NULLIF($6, '')
FROM urls
WHERE short_code = $1
  AND deleted_at IS NULL
  AND (expires_at IS NULL OR expires_at > now())`

	clickedAt := metadata.At
	if clickedAt.IsZero() {
		clickedAt = time.Now().UTC()
	}
	tag, err := r.db.Exec(ctx, query, code, clickedAt, metadata.Referrer, metadata.UserAgent, metadata.IPHash, metadata.Country)
	if err != nil {
		return mapPostgresError(err)
	}
	if tag.RowsAffected() == 0 {
		return service.ErrNotFound
	}
	return nil
}

func (r *URLRepository) Analytics(ctx context.Context, code string) (models.AnalyticsSummary, error) {
	summary := models.AnalyticsSummary{
		ShortCode:   code,
		ByReferrer:  map[string]int64{},
		ByCountry:   map[string]int64{},
		ByUserAgent: map[string]int64{},
		ByBucket:    []models.AnalyticsBucket{},
	}

	if err := r.db.QueryRow(ctx, `SELECT count(*) FROM url_clicks WHERE short_code = $1`, code).Scan(&summary.TotalClicks); err != nil {
		return models.AnalyticsSummary{}, mapPostgresError(err)
	}

	if err := scanDimension(ctx, r.db, summary.ByReferrer, `
SELECT COALESCE(referrer, 'direct') AS dimension, count(*)
FROM url_clicks
WHERE short_code = $1
GROUP BY dimension
ORDER BY count(*) DESC
LIMIT 20`, code); err != nil {
		return models.AnalyticsSummary{}, err
	}

	if err := scanDimension(ctx, r.db, summary.ByCountry, `
SELECT COALESCE(country_code, 'unknown') AS dimension, count(*)
FROM url_clicks
WHERE short_code = $1
GROUP BY dimension
ORDER BY count(*) DESC
LIMIT 20`, code); err != nil {
		return models.AnalyticsSummary{}, err
	}

	if err := scanDimension(ctx, r.db, summary.ByUserAgent, `
SELECT COALESCE(user_agent, 'unknown') AS dimension, count(*)
FROM url_clicks
WHERE short_code = $1
GROUP BY dimension
ORDER BY count(*) DESC
LIMIT 20`, code); err != nil {
		return models.AnalyticsSummary{}, err
	}

	rows, err := r.db.Query(ctx, `
SELECT date_trunc('hour', clicked_at) AS bucket, count(*)
FROM url_clicks
WHERE short_code = $1
GROUP BY bucket
ORDER BY bucket ASC`, code)
	if err != nil {
		return models.AnalyticsSummary{}, mapPostgresError(err)
	}
	defer rows.Close()

	for rows.Next() {
		var bucket models.AnalyticsBucket
		if err := rows.Scan(&bucket.Timestamp, &bucket.Clicks); err != nil {
			return models.AnalyticsSummary{}, mapPostgresError(err)
		}
		summary.ByBucket = append(summary.ByBucket, bucket)
	}
	if err := rows.Err(); err != nil {
		return models.AnalyticsSummary{}, mapPostgresError(err)
	}

	return summary, nil
}

type queryer interface {
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
}

func scanDimension(ctx context.Context, q queryer, target map[string]int64, query string, code string) error {
	rows, err := q.Query(ctx, query, code)
	if err != nil {
		return mapPostgresError(err)
	}
	defer rows.Close()

	for rows.Next() {
		var key string
		var count int64
		if err := rows.Scan(&key, &count); err != nil {
			return mapPostgresError(err)
		}
		target[key] = count
	}
	if err := rows.Err(); err != nil {
		return mapPostgresError(err)
	}
	return nil
}

func mapPostgresError(err error) error {
	if errors.Is(err, pgx.ErrNoRows) {
		return service.ErrNotFound
	}

	var pgErr *pgconn.PgError
	if errors.As(err, &pgErr) {
		if pgErr.Code == "23505" {
			return service.ErrConflict
		}
	}

	return fmt.Errorf("postgres operation failed: %w", err)
}
