package service

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"math/big"
	"net/url"
	"regexp"
	"strings"
	"time"

	"github.com/example/url-shortener-service/internal/models"
)

var (
	ErrNotFound   = errors.New("url not found")
	ErrConflict   = errors.New("short code already exists")
	ErrValidation = errors.New("validation failed")
)

type URLRepository interface {
	Create(ctx context.Context, item models.URL) (models.URL, error)
	GetByCode(ctx context.Context, code string) (models.URL, error)
	List(ctx context.Context, limit int) ([]models.URL, error)
	Update(ctx context.Context, code string, req models.UpdateURLRequest) (models.URL, error)
	Delete(ctx context.Context, code string) error
	RecordClick(ctx context.Context, code string, metadata ClickMetadata) error
	Analytics(ctx context.Context, code string) (models.AnalyticsSummary, error)
}

type URLCache interface {
	Get(ctx context.Context, code string) (models.URL, error)
	Set(ctx context.Context, item models.URL) error
	Delete(ctx context.Context, code string) error
	RecordClick(ctx context.Context, code string, metadata ClickMetadata) error
}

type ClickMetadata struct {
	Referrer  string
	UserAgent string
	IP        string
	IPHash    string
	Country   string
	At        time.Time
}

type URLService struct {
	repo          URLRepository
	cache         URLCache
	publicBaseURL string
}

var (
	shortCodePattern = regexp.MustCompile(`^[A-Za-z0-9_-]{3,64}$`)
	reservedCodes    = map[string]struct{}{
		"admin": {}, "api": {}, "assets": {}, "docs": {}, "health": {}, "login": {},
		"logout": {}, "metrics": {}, "static": {}, "urls": {}, "www": {},
	}
)

func NewURLService(repo URLRepository, cache URLCache, publicBaseURL string) *URLService {
	return &URLService{
		repo:          repo,
		cache:         cache,
		publicBaseURL: strings.TrimRight(publicBaseURL, "/"),
	}
}

func (s *URLService) Create(ctx context.Context, req models.CreateURLRequest) (models.URLResponse, error) {
	normalized, err := validateOriginalURL(req.OriginalURL)
	if err != nil {
		return models.URLResponse{}, err
	}

	code := strings.TrimSpace(req.CustomCode)
	if code != "" {
		if err := validateShortCode(code); err != nil {
			return models.URLResponse{}, err
		}
	}

	now := time.Now().UTC()
	if req.ExpiresAt != nil && !req.ExpiresAt.After(now) {
		return models.URLResponse{}, fmt.Errorf("%w: expiration must be in the future", ErrValidation)
	}

	var lastErr error
	for attempt := 0; attempt < 5; attempt++ {
		candidate := code
		if candidate == "" {
			candidate, err = generateShortCode(8)
			if err != nil {
				return models.URLResponse{}, fmt.Errorf("generate short code: %w", err)
			}
		}

		item := models.URL{
			ShortCode:   candidate,
			OriginalURL: normalized,
			Title:       strings.TrimSpace(req.Title),
			CreatedAt:   now,
			UpdatedAt:   now,
			ExpiresAt:   req.ExpiresAt,
		}

		created, err := s.repo.Create(ctx, item)
		if err == nil {
			_ = s.cache.Set(ctx, created)
			return s.toResponse(created), nil
		}
		if !errors.Is(err, ErrConflict) || code != "" {
			return models.URLResponse{}, err
		}
		lastErr = err
	}

	if lastErr != nil {
		return models.URLResponse{}, lastErr
	}
	return models.URLResponse{}, ErrConflict
}

func (s *URLService) Get(ctx context.Context, code string) (models.URLResponse, error) {
	item, err := s.resolve(ctx, code)
	if err != nil {
		return models.URLResponse{}, err
	}
	return s.toResponse(item), nil
}

func (s *URLService) List(ctx context.Context, limit int) (models.ListURLsResponse, error) {
	if limit <= 0 || limit > 100 {
		limit = 50
	}

	items, err := s.repo.List(ctx, limit)
	if err != nil {
		return models.ListURLsResponse{}, err
	}

	resp := models.ListURLsResponse{Items: make([]models.URLResponse, 0, len(items)), Limit: limit}
	for _, item := range items {
		resp.Items = append(resp.Items, s.toResponse(item))
	}
	return resp, nil
}

func (s *URLService) Resolve(ctx context.Context, code string, metadata ClickMetadata) (string, error) {
	item, err := s.resolve(ctx, code)
	if err != nil {
		return "", err
	}

	metadata.At = time.Now().UTC()
	metadata.IPHash = hashIP(metadata.IP)
	_ = s.cache.RecordClick(ctx, code, metadata)
	_ = s.repo.RecordClick(ctx, code, metadata)

	return item.OriginalURL, nil
}

func (s *URLService) Update(ctx context.Context, code string, req models.UpdateURLRequest) (models.URLResponse, error) {
	if err := validateShortCode(code); err != nil {
		return models.URLResponse{}, err
	}
	if req.OriginalURL != "" {
		normalized, err := validateOriginalURL(req.OriginalURL)
		if err != nil {
			return models.URLResponse{}, err
		}
		req.OriginalURL = normalized
	}
	if req.ExpiresAt != nil && !req.ExpiresAt.After(time.Now().UTC()) {
		return models.URLResponse{}, fmt.Errorf("%w: expiration must be in the future", ErrValidation)
	}

	updated, err := s.repo.Update(ctx, code, req)
	if err != nil {
		return models.URLResponse{}, err
	}

	_ = s.cache.Set(ctx, updated)
	return s.toResponse(updated), nil
}

func (s *URLService) Delete(ctx context.Context, code string) error {
	if err := validateShortCode(code); err != nil {
		return err
	}
	if err := s.repo.Delete(ctx, code); err != nil {
		return err
	}
	_ = s.cache.Delete(ctx, code)
	return nil
}

func (s *URLService) Analytics(ctx context.Context, code string) (models.AnalyticsSummary, error) {
	if _, err := s.resolve(ctx, code); err != nil {
		return models.AnalyticsSummary{}, err
	}
	return s.repo.Analytics(ctx, code)
}

func (s *URLService) resolve(ctx context.Context, code string) (models.URL, error) {
	if err := validateShortCode(code); err != nil {
		return models.URL{}, err
	}

	if cached, err := s.cache.Get(ctx, code); err == nil {
		return cached, nil
	}

	item, err := s.repo.GetByCode(ctx, code)
	if err != nil {
		return models.URL{}, err
	}
	_ = s.cache.Set(ctx, item)
	return item, nil
}

func (s *URLService) toResponse(item models.URL) models.URLResponse {
	return models.URLResponse{
		ID:          item.ID,
		ShortCode:   item.ShortCode,
		ShortURL:    s.publicBaseURL + "/" + item.ShortCode,
		OriginalURL: item.OriginalURL,
		Title:       item.Title,
		CreatedAt:   item.CreatedAt,
		UpdatedAt:   item.UpdatedAt,
		ExpiresAt:   item.ExpiresAt,
	}
}

func validateOriginalURL(raw string) (string, error) {
	parsed, err := url.ParseRequestURI(strings.TrimSpace(raw))
	if err != nil {
		return "", fmt.Errorf("%w: invalid url", ErrValidation)
	}
	if parsed.Scheme != "http" && parsed.Scheme != "https" {
		return "", fmt.Errorf("%w: unsupported url scheme", ErrValidation)
	}
	if parsed.Host == "" {
		return "", fmt.Errorf("%w: missing url host", ErrValidation)
	}
	parsed.Fragment = ""
	return parsed.String(), nil
}

func validateShortCode(code string) error {
	code = strings.TrimSpace(code)
	if !shortCodePattern.MatchString(code) {
		return fmt.Errorf("%w: short code must be 3-64 characters using letters, numbers, underscore, or dash", ErrValidation)
	}
	if _, exists := reservedCodes[strings.ToLower(code)]; exists {
		return fmt.Errorf("%w: short code is reserved", ErrValidation)
	}
	return nil
}

func generateShortCode(length int) (string, error) {
	const alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
	var builder strings.Builder
	builder.Grow(length)

	max := big.NewInt(int64(len(alphabet)))
	for i := 0; i < length; i++ {
		n, err := rand.Int(rand.Reader, max)
		if err != nil {
			return "", err
		}
		builder.WriteByte(alphabet[n.Int64()])
	}
	return builder.String(), nil
}

func hashIP(ip string) string {
	ip = strings.TrimSpace(ip)
	if ip == "" {
		return ""
	}
	sum := sha256.Sum256([]byte(ip))
	return hex.EncodeToString(sum[:])
}
