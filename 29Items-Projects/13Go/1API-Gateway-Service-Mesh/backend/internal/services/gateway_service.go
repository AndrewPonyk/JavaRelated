package services

import (
	"context"
	"errors"
	"fmt"
	"net/url"
	"strings"

	"github.com/example/api-gateway/backend/internal/models"
	"github.com/example/api-gateway/backend/internal/repository"
)

var ErrInvalidInput = errors.New("invalid input")

type GatewayService struct {
	repo repository.GatewayRepository
}

func NewGatewayService(repo repository.GatewayRepository) *GatewayService {
	return &GatewayService{repo: repo}
}

func (s *GatewayService) ListTenants(ctx context.Context, opts models.ListOptions) ([]models.Tenant, error) {
	return s.repo.ListTenants(ctx, normalizeListOptions(opts))
}

func (s *GatewayService) GetTenant(ctx context.Context, id string) (models.Tenant, error) {
	return s.repo.GetTenant(ctx, id)
}

func (s *GatewayService) CreateTenant(ctx context.Context, input models.TenantInput) (models.Tenant, error) {
	normalized, err := normalizeTenant(input)
	if err != nil {
		return models.Tenant{}, err
	}
	return s.repo.CreateTenant(ctx, normalized)
}

func (s *GatewayService) UpdateTenant(ctx context.Context, id string, input models.TenantInput) (models.Tenant, error) {
	if strings.TrimSpace(id) == "" {
		return models.Tenant{}, ErrInvalidInput
	}
	normalized, err := normalizeTenant(input)
	if err != nil {
		return models.Tenant{}, err
	}
	return s.repo.UpdateTenant(ctx, id, normalized)
}

func (s *GatewayService) DeleteTenant(ctx context.Context, id string) error {
	if strings.TrimSpace(id) == "" {
		return ErrInvalidInput
	}
	return s.repo.DeleteTenant(ctx, id)
}

func (s *GatewayService) ListRoutes(ctx context.Context, tenantID string, opts models.ListOptions) ([]models.Route, error) {
	return s.repo.ListRoutes(ctx, tenantID, normalizeListOptions(opts))
}

func (s *GatewayService) GetRoute(ctx context.Context, id string) (models.Route, error) {
	return s.repo.GetRoute(ctx, id)
}

func (s *GatewayService) CreateRoute(ctx context.Context, input models.RouteInput) (models.Route, error) {
	route, err := routeFromInput("", input)
	if err != nil {
		return models.Route{}, err
	}
	return s.repo.CreateRoute(ctx, route)
}

func (s *GatewayService) UpdateRoute(ctx context.Context, id string, input models.RouteInput) (models.Route, error) {
	if strings.TrimSpace(id) == "" {
		return models.Route{}, ErrInvalidInput
	}
	route, err := routeFromInput(id, input)
	if err != nil {
		return models.Route{}, err
	}
	return s.repo.UpdateRoute(ctx, route)
}

func (s *GatewayService) DeleteRoute(ctx context.Context, id string) error {
	if strings.TrimSpace(id) == "" {
		return ErrInvalidInput
	}
	return s.repo.DeleteRoute(ctx, id)
}

func (s *GatewayService) MatchRoute(ctx context.Context, match models.RouteMatch) (models.Route, error) {
	if strings.TrimSpace(match.Host) == "" || strings.TrimSpace(match.Path) == "" || strings.TrimSpace(match.Method) == "" {
		return models.Route{}, ErrInvalidInput
	}
	match.Host = strings.ToLower(strings.TrimSpace(match.Host))
	match.Method = strings.ToUpper(strings.TrimSpace(match.Method))
	if !strings.HasPrefix(match.Path, "/") {
		match.Path = "/" + match.Path
	}
	return s.repo.MatchRoute(ctx, match)
}

func (s *GatewayService) ListAnomalyEvents(ctx context.Context, tenantID string, routeID string, opts models.ListOptions) ([]models.AnomalyEvent, error) {
	return s.repo.ListAnomalyEvents(ctx, tenantID, routeID, normalizeListOptions(opts))
}

func (s *GatewayService) GetAnomalyEvent(ctx context.Context, id string) (models.AnomalyEvent, error) {
	return s.repo.GetAnomalyEvent(ctx, id)
}

func (s *GatewayService) CreateAnomalyEvent(ctx context.Context, input models.AnomalyEventInput) (models.AnomalyEvent, error) {
	normalized, err := normalizeAnomalyInput(input)
	if err != nil {
		return models.AnomalyEvent{}, err
	}
	return s.repo.CreateAnomalyEvent(ctx, normalized)
}

func (s *GatewayService) UpdateAnomalyEvent(ctx context.Context, id string, input models.AnomalyEventInput) (models.AnomalyEvent, error) {
	if strings.TrimSpace(id) == "" {
		return models.AnomalyEvent{}, ErrInvalidInput
	}
	normalized, err := normalizeAnomalyInput(input)
	if err != nil {
		return models.AnomalyEvent{}, err
	}
	return s.repo.UpdateAnomalyEvent(ctx, id, normalized)
}

func (s *GatewayService) DeleteAnomalyEvent(ctx context.Context, id string) error {
	if strings.TrimSpace(id) == "" {
		return ErrInvalidInput
	}
	return s.repo.DeleteAnomalyEvent(ctx, id)
}

func normalizeTenant(input models.TenantInput) (models.TenantInput, error) {
	input.Name = strings.TrimSpace(input.Name)
	input.Status = strings.TrimSpace(input.Status)
	if input.Name == "" {
		return input, fmt.Errorf("%w: tenant name is required", ErrInvalidInput)
	}
	if input.Status == "" {
		input.Status = "active"
	}
	if input.Status != "active" && input.Status != "suspended" {
		return input, fmt.Errorf("%w: tenant status must be active or suspended", ErrInvalidInput)
	}
	return input, nil
}

func routeFromInput(id string, input models.RouteInput) (models.Route, error) {
	input.TenantID = strings.TrimSpace(input.TenantID)
	input.Name = strings.TrimSpace(input.Name)
	input.Host = strings.ToLower(strings.TrimSpace(input.Host))
	input.PathPrefix = strings.TrimSpace(input.PathPrefix)
	input.UpstreamService = strings.TrimSpace(input.UpstreamService)
	input.UpstreamProtocol = strings.ToLower(strings.TrimSpace(input.UpstreamProtocol))

	if input.TenantID == "" || input.Name == "" || input.Host == "" || input.PathPrefix == "" ||
		input.UpstreamService == "" || input.UpstreamProtocol == "" || len(input.Methods) == 0 {
		return models.Route{}, fmt.Errorf("%w: missing required route fields", ErrInvalidInput)
	}
	if !strings.HasPrefix(input.PathPrefix, "/") {
		return models.Route{}, fmt.Errorf("%w: pathPrefix must start with /", ErrInvalidInput)
	}
	if input.UpstreamProtocol != "http" && input.UpstreamProtocol != "grpc" {
		return models.Route{}, fmt.Errorf("%w: upstreamProtocol must be http or grpc", ErrInvalidInput)
	}
	if input.RateLimitPerMin < 0 {
		return models.Route{}, fmt.Errorf("%w: rateLimitPerMinute cannot be negative", ErrInvalidInput)
	}
	if input.UpstreamProtocol == "http" {
		parsed, err := url.Parse(input.UpstreamService)
		if err != nil || parsed.Scheme == "" || parsed.Host == "" {
			return models.Route{}, fmt.Errorf("%w: http upstreamService must be an absolute URL", ErrInvalidInput)
		}
	}

	route := models.Route{
		ID:                id,
		TenantID:          input.TenantID,
		Name:              input.Name,
		Host:              input.Host,
		PathPrefix:        input.PathPrefix,
		Methods:           normalizeMethods(input.Methods),
		UpstreamService:   input.UpstreamService,
		UpstreamProtocol:  input.UpstreamProtocol,
		RateLimitPerMin:   input.RateLimitPerMin,
		RequiredScopes:    normalizeList(input.RequiredScopes),
		TransformHeaders:  input.TransformHeaders,
		AnomalyProtection: input.AnomalyProtection,
	}
	if route.TransformHeaders == nil {
		route.TransformHeaders = map[string]string{}
	}
	if len(route.Methods) == 0 {
		return models.Route{}, fmt.Errorf("%w: at least one valid method is required", ErrInvalidInput)
	}
	return route, nil
}

func normalizeAnomalyInput(input models.AnomalyEventInput) (models.AnomalyEventInput, error) {
	input.RouteID = strings.TrimSpace(input.RouteID)
	input.TenantID = strings.TrimSpace(input.TenantID)
	input.Reason = strings.TrimSpace(input.Reason)
	if input.RouteID == "" || input.TenantID == "" || input.Reason == "" {
		return input, fmt.Errorf("%w: routeId, tenantId, and reason are required", ErrInvalidInput)
	}
	if input.Score < 0 || input.Score > 1 {
		return input, fmt.Errorf("%w: score must be between 0 and 1", ErrInvalidInput)
	}
	if input.Features == nil {
		input.Features = map[string]any{}
	}
	return input, nil
}

func normalizeMethods(methods []string) []string {
	return normalizeListWithTransform(methods, strings.ToUpper)
}

func normalizeList(values []string) []string {
	return normalizeListWithTransform(values, func(value string) string { return value })
}

func normalizeListWithTransform(values []string, transform func(string) string) []string {
	seen := map[string]struct{}{}
	normalized := make([]string, 0, len(values))
	for _, value := range values {
		item := transform(strings.TrimSpace(value))
		if item == "" {
			continue
		}
		if _, ok := seen[item]; ok {
			continue
		}
		seen[item] = struct{}{}
		normalized = append(normalized, item)
	}
	return normalized
}

func normalizeListOptions(opts models.ListOptions) models.ListOptions {
	if opts.Limit <= 0 {
		opts.Limit = 100
	}
	if opts.Limit > 250 {
		opts.Limit = 250
	}
	if opts.Offset < 0 {
		opts.Offset = 0
	}
	return opts
}
