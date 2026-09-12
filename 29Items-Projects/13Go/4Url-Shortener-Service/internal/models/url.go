package models

import "time"

type URL struct {
	ID          string     `json:"id"`
	ShortCode   string     `json:"shortCode"`
	OriginalURL string     `json:"originalUrl"`
	Title       string     `json:"title,omitempty"`
	OwnerKeyID  *string    `json:"ownerKeyId,omitempty"`
	CreatedAt   time.Time  `json:"createdAt"`
	UpdatedAt   time.Time  `json:"updatedAt"`
	ExpiresAt   *time.Time `json:"expiresAt,omitempty"`
	DeletedAt   *time.Time `json:"deletedAt,omitempty"`
}

type CreateURLRequest struct {
	OriginalURL string     `json:"originalUrl" binding:"required,url"`
	CustomCode  string     `json:"customCode,omitempty" binding:"omitempty,min=3,max=64"`
	Title       string     `json:"title,omitempty" binding:"omitempty,max=200"`
	ExpiresAt   *time.Time `json:"expiresAt,omitempty"`
}

type UpdateURLRequest struct {
	OriginalURL string     `json:"originalUrl,omitempty" binding:"omitempty,url"`
	Title       string     `json:"title,omitempty" binding:"omitempty,max=200"`
	ExpiresAt   *time.Time `json:"expiresAt,omitempty"`
}

type URLResponse struct {
	ID          string     `json:"id"`
	ShortCode   string     `json:"shortCode"`
	ShortURL    string     `json:"shortUrl"`
	OriginalURL string     `json:"originalUrl"`
	Title       string     `json:"title,omitempty"`
	CreatedAt   time.Time  `json:"createdAt"`
	UpdatedAt   time.Time  `json:"updatedAt"`
	ExpiresAt   *time.Time `json:"expiresAt,omitempty"`
}

type ListURLsResponse struct {
	Items []URLResponse `json:"items"`
	Limit int           `json:"limit"`
}

type AnalyticsSummary struct {
	ShortCode   string            `json:"shortCode"`
	TotalClicks int64             `json:"totalClicks"`
	ByReferrer  map[string]int64  `json:"byReferrer,omitempty"`
	ByCountry   map[string]int64  `json:"byCountry,omitempty"`
	ByUserAgent map[string]int64  `json:"byUserAgent,omitempty"`
	ByBucket    []AnalyticsBucket `json:"byBucket,omitempty"`
}

type AnalyticsBucket struct {
	Timestamp time.Time `json:"timestamp"`
	Clicks    int64     `json:"clicks"`
}
