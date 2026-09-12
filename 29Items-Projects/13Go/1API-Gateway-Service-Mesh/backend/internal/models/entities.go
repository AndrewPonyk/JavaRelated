package models

import "time"

type Tenant struct {
	ID        string    `json:"id"`
	Name      string    `json:"name"`
	Status    string    `json:"status"`
	CreatedAt time.Time `json:"createdAt"`
	UpdatedAt time.Time `json:"updatedAt"`
}

type TenantInput struct {
	Name   string `json:"name" binding:"required"`
	Status string `json:"status"`
}

type AnomalyEvent struct {
	ID         string                 `json:"id"`
	RouteID    string                 `json:"routeId"`
	TenantID   string                 `json:"tenantId"`
	Score      float64                `json:"score"`
	Reason     string                 `json:"reason"`
	Features   map[string]any         `json:"features"`
	ObservedAt time.Time              `json:"observedAt"`
}

type AnomalyEventInput struct {
	RouteID  string         `json:"routeId" binding:"required"`
	TenantID string         `json:"tenantId" binding:"required"`
	Score    float64        `json:"score" binding:"required"`
	Reason   string         `json:"reason" binding:"required"`
	Features map[string]any `json:"features" binding:"required"`
}

type RouteMatch struct {
	TenantID string
	Host     string
	Path     string
	Method   string
}

type ListOptions struct {
	Limit  int
	Offset int
}
