package models

import "time"

type Route struct {
	ID                string            `json:"id"`
	TenantID          string            `json:"tenantId"`
	Name              string            `json:"name"`
	Host              string            `json:"host"`
	PathPrefix        string            `json:"pathPrefix"`
	Methods           []string          `json:"methods"`
	UpstreamService   string            `json:"upstreamService"`
	UpstreamProtocol  string            `json:"upstreamProtocol"`
	RateLimitPerMin   int               `json:"rateLimitPerMinute"`
	RequiredScopes    []string          `json:"requiredScopes"`
	TransformHeaders  map[string]string `json:"transformHeaders"`
	AnomalyProtection bool              `json:"anomalyProtection"`
	CreatedAt         time.Time         `json:"createdAt"`
	UpdatedAt         time.Time         `json:"updatedAt"`
}

type RouteInput struct {
	TenantID          string            `json:"tenantId" binding:"required"`
	Name              string            `json:"name" binding:"required"`
	Host              string            `json:"host" binding:"required"`
	PathPrefix        string            `json:"pathPrefix" binding:"required"`
	Methods           []string          `json:"methods" binding:"required"`
	UpstreamService   string            `json:"upstreamService" binding:"required"`
	UpstreamProtocol  string            `json:"upstreamProtocol" binding:"required"`
	RateLimitPerMin   int               `json:"rateLimitPerMinute"`
	RequiredScopes    []string          `json:"requiredScopes"`
	TransformHeaders  map[string]string `json:"transformHeaders"`
	AnomalyProtection bool              `json:"anomalyProtection"`
}
