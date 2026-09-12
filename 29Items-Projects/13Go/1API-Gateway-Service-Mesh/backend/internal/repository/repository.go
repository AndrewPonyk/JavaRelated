package repository

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/example/api-gateway/backend/internal/models"
	"github.com/lib/pq"
)

var (
	ErrNotFound   = errors.New("resource not found")
	ErrConflict   = errors.New("resource conflict")
	ErrInvalidRef = errors.New("invalid resource reference")
	ErrRepository = errors.New("repository error")
)

type GatewayRepository interface {
	ListTenants(ctx context.Context, opts models.ListOptions) ([]models.Tenant, error)
	GetTenant(ctx context.Context, id string) (models.Tenant, error)
	CreateTenant(ctx context.Context, input models.TenantInput) (models.Tenant, error)
	UpdateTenant(ctx context.Context, id string, input models.TenantInput) (models.Tenant, error)
	DeleteTenant(ctx context.Context, id string) error

	ListRoutes(ctx context.Context, tenantID string, opts models.ListOptions) ([]models.Route, error)
	GetRoute(ctx context.Context, id string) (models.Route, error)
	CreateRoute(ctx context.Context, route models.Route) (models.Route, error)
	UpdateRoute(ctx context.Context, route models.Route) (models.Route, error)
	DeleteRoute(ctx context.Context, id string) error
	MatchRoute(ctx context.Context, match models.RouteMatch) (models.Route, error)

	ListAnomalyEvents(ctx context.Context, tenantID string, routeID string, opts models.ListOptions) ([]models.AnomalyEvent, error)
	GetAnomalyEvent(ctx context.Context, id string) (models.AnomalyEvent, error)
	CreateAnomalyEvent(ctx context.Context, input models.AnomalyEventInput) (models.AnomalyEvent, error)
	UpdateAnomalyEvent(ctx context.Context, id string, input models.AnomalyEventInput) (models.AnomalyEvent, error)
	DeleteAnomalyEvent(ctx context.Context, id string) error
}

type PostgresRepository struct {
	db *sql.DB
}

func NewPostgresRepository(db *sql.DB) *PostgresRepository {
	return &PostgresRepository{db: db}
}

func (r *PostgresRepository) ListTenants(ctx context.Context, opts models.ListOptions) ([]models.Tenant, error) {
	opts = normalizeListOptions(opts)
	rows, err := r.db.QueryContext(ctx, `
		SELECT id::text, name, status, created_at, updated_at
		FROM tenants
		ORDER BY name
		LIMIT $1 OFFSET $2`, opts.Limit, opts.Offset)
	if err != nil {
		return nil, wrapDBError(err)
	}
	defer rows.Close()

	tenants := []models.Tenant{}
	for rows.Next() {
		var tenant models.Tenant
		if err := rows.Scan(&tenant.ID, &tenant.Name, &tenant.Status, &tenant.CreatedAt, &tenant.UpdatedAt); err != nil {
			return nil, wrapDBError(err)
		}
		tenants = append(tenants, tenant)
	}
	return tenants, wrapDBError(rows.Err())
}

func (r *PostgresRepository) GetTenant(ctx context.Context, id string) (models.Tenant, error) {
	var tenant models.Tenant
	err := r.db.QueryRowContext(ctx, `
		SELECT id::text, name, status, created_at, updated_at
		FROM tenants
		WHERE id = $1`, id).Scan(&tenant.ID, &tenant.Name, &tenant.Status, &tenant.CreatedAt, &tenant.UpdatedAt)
	return tenant, wrapDBError(err)
}

func (r *PostgresRepository) CreateTenant(ctx context.Context, input models.TenantInput) (models.Tenant, error) {
	status := input.Status
	if status == "" {
		status = "active"
	}
	var tenant models.Tenant
	err := r.db.QueryRowContext(ctx, `
		INSERT INTO tenants (name, status)
		VALUES ($1, $2)
		RETURNING id::text, name, status, created_at, updated_at`,
		input.Name, status).Scan(&tenant.ID, &tenant.Name, &tenant.Status, &tenant.CreatedAt, &tenant.UpdatedAt)
	return tenant, wrapDBError(err)
}

func (r *PostgresRepository) UpdateTenant(ctx context.Context, id string, input models.TenantInput) (models.Tenant, error) {
	status := input.Status
	if status == "" {
		status = "active"
	}
	var tenant models.Tenant
	err := r.db.QueryRowContext(ctx, `
		UPDATE tenants
		SET name = $2, status = $3, updated_at = now()
		WHERE id = $1
		RETURNING id::text, name, status, created_at, updated_at`,
		id, input.Name, status).Scan(&tenant.ID, &tenant.Name, &tenant.Status, &tenant.CreatedAt, &tenant.UpdatedAt)
	return tenant, wrapDBError(err)
}

func (r *PostgresRepository) DeleteTenant(ctx context.Context, id string) error {
	result, err := r.db.ExecContext(ctx, `DELETE FROM tenants WHERE id = $1`, id)
	return wrapExecResult(result, err)
}

func (r *PostgresRepository) ListRoutes(ctx context.Context, tenantID string, opts models.ListOptions) ([]models.Route, error) {
	opts = normalizeListOptions(opts)
	query := `
		SELECT id::text, tenant_id::text, name, host, path_prefix, methods, upstream_service,
		       upstream_protocol, rate_limit_per_minute, required_scopes, transform_headers,
		       anomaly_protection, created_at, updated_at
		FROM gateway_routes`
	args := []any{}
	if tenantID != "" {
		query += ` WHERE tenant_id = $1`
		args = append(args, tenantID)
	}
	args = append(args, opts.Limit, opts.Offset)
	query += fmt.Sprintf(` ORDER BY host, path_prefix, name LIMIT $%d OFFSET $%d`, len(args)-1, len(args))

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, wrapDBError(err)
	}
	defer rows.Close()

	routes := []models.Route{}
	for rows.Next() {
		route, err := scanRoute(rows)
		if err != nil {
			return nil, err
		}
		routes = append(routes, route)
	}
	return routes, wrapDBError(rows.Err())
}

func (r *PostgresRepository) GetRoute(ctx context.Context, id string) (models.Route, error) {
	row := r.db.QueryRowContext(ctx, `
		SELECT id::text, tenant_id::text, name, host, path_prefix, methods, upstream_service,
		       upstream_protocol, rate_limit_per_minute, required_scopes, transform_headers,
		       anomaly_protection, created_at, updated_at
		FROM gateway_routes
		WHERE id = $1`, id)
	return scanRoute(row)
}

func (r *PostgresRepository) CreateRoute(ctx context.Context, route models.Route) (models.Route, error) {
	headers, err := json.Marshal(route.TransformHeaders)
	if err != nil {
		return models.Route{}, fmt.Errorf("%w: encode route headers: %v", ErrRepository, err)
	}
	row := r.db.QueryRowContext(ctx, `
		INSERT INTO gateway_routes
		    (tenant_id, name, host, path_prefix, methods, upstream_service, upstream_protocol,
		     rate_limit_per_minute, required_scopes, transform_headers, anomaly_protection)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)
		RETURNING id::text, tenant_id::text, name, host, path_prefix, methods, upstream_service,
		          upstream_protocol, rate_limit_per_minute, required_scopes, transform_headers,
		          anomaly_protection, created_at, updated_at`,
		route.TenantID, route.Name, route.Host, route.PathPrefix, pq.Array(route.Methods), route.UpstreamService,
		route.UpstreamProtocol, route.RateLimitPerMin, pq.Array(route.RequiredScopes), headers, route.AnomalyProtection)
	return scanRoute(row)
}

func (r *PostgresRepository) UpdateRoute(ctx context.Context, route models.Route) (models.Route, error) {
	headers, err := json.Marshal(route.TransformHeaders)
	if err != nil {
		return models.Route{}, fmt.Errorf("%w: encode route headers: %v", ErrRepository, err)
	}
	row := r.db.QueryRowContext(ctx, `
		UPDATE gateway_routes
		SET tenant_id = $2, name = $3, host = $4, path_prefix = $5, methods = $6,
		    upstream_service = $7, upstream_protocol = $8, rate_limit_per_minute = $9,
		    required_scopes = $10, transform_headers = $11, anomaly_protection = $12,
		    updated_at = now()
		WHERE id = $1
		RETURNING id::text, tenant_id::text, name, host, path_prefix, methods, upstream_service,
		          upstream_protocol, rate_limit_per_minute, required_scopes, transform_headers,
		          anomaly_protection, created_at, updated_at`,
		route.ID, route.TenantID, route.Name, route.Host, route.PathPrefix, pq.Array(route.Methods), route.UpstreamService,
		route.UpstreamProtocol, route.RateLimitPerMin, pq.Array(route.RequiredScopes), headers, route.AnomalyProtection)
	return scanRoute(row)
}

func (r *PostgresRepository) DeleteRoute(ctx context.Context, id string) error {
	result, err := r.db.ExecContext(ctx, `DELETE FROM gateway_routes WHERE id = $1`, id)
	return wrapExecResult(result, err)
}

func (r *PostgresRepository) MatchRoute(ctx context.Context, match models.RouteMatch) (models.Route, error) {
	args := []any{strings.ToLower(match.Host), match.Path, strings.ToUpper(match.Method)}
	tenantFilter := ""
	if match.TenantID != "" {
		args = append(args, match.TenantID)
		tenantFilter = "AND tenant_id = $4"
	}
	row := r.db.QueryRowContext(ctx, fmt.Sprintf(`
		SELECT id::text, tenant_id::text, name, host, path_prefix, methods, upstream_service,
		       upstream_protocol, rate_limit_per_minute, required_scopes, transform_headers,
		       anomaly_protection, created_at, updated_at
		FROM gateway_routes
		WHERE lower(host) = $1
		  AND $2 LIKE path_prefix || '%%'
		  AND $3 = ANY(methods)
		  %s
		ORDER BY length(path_prefix) DESC
		LIMIT 1`, tenantFilter), args...)
	return scanRoute(row)
}

func (r *PostgresRepository) ListAnomalyEvents(ctx context.Context, tenantID string, routeID string, opts models.ListOptions) ([]models.AnomalyEvent, error) {
	opts = normalizeListOptions(opts)
	query := `
		SELECT id::text, route_id::text, tenant_id::text, score::float8, reason, features, observed_at
		FROM anomaly_events`
	args := []any{}
	clauses := []string{}
	if tenantID != "" {
		args = append(args, tenantID)
		clauses = append(clauses, fmt.Sprintf("tenant_id = $%d", len(args)))
	}
	if routeID != "" {
		args = append(args, routeID)
		clauses = append(clauses, fmt.Sprintf("route_id = $%d", len(args)))
	}
	if len(clauses) > 0 {
		query += " WHERE " + strings.Join(clauses, " AND ")
	}
	args = append(args, opts.Limit, opts.Offset)
	query += fmt.Sprintf(" ORDER BY observed_at DESC LIMIT $%d OFFSET $%d", len(args)-1, len(args))

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, wrapDBError(err)
	}
	defer rows.Close()

	events := []models.AnomalyEvent{}
	for rows.Next() {
		event, err := scanAnomaly(rows)
		if err != nil {
			return nil, err
		}
		events = append(events, event)
	}
	return events, wrapDBError(rows.Err())
}

func (r *PostgresRepository) GetAnomalyEvent(ctx context.Context, id string) (models.AnomalyEvent, error) {
	row := r.db.QueryRowContext(ctx, `
		SELECT id::text, route_id::text, tenant_id::text, score::float8, reason, features, observed_at
		FROM anomaly_events
		WHERE id = $1`, id)
	return scanAnomaly(row)
}

func (r *PostgresRepository) CreateAnomalyEvent(ctx context.Context, input models.AnomalyEventInput) (models.AnomalyEvent, error) {
	features, err := json.Marshal(input.Features)
	if err != nil {
		return models.AnomalyEvent{}, fmt.Errorf("%w: encode anomaly features: %v", ErrRepository, err)
	}
	row := r.db.QueryRowContext(ctx, `
		INSERT INTO anomaly_events (route_id, tenant_id, score, reason, features)
		VALUES ($1, $2, $3, $4, $5)
		RETURNING id::text, route_id::text, tenant_id::text, score::float8, reason, features, observed_at`,
		input.RouteID, input.TenantID, input.Score, input.Reason, features)
	return scanAnomaly(row)
}

func (r *PostgresRepository) UpdateAnomalyEvent(ctx context.Context, id string, input models.AnomalyEventInput) (models.AnomalyEvent, error) {
	features, err := json.Marshal(input.Features)
	if err != nil {
		return models.AnomalyEvent{}, fmt.Errorf("%w: encode anomaly features: %v", ErrRepository, err)
	}
	row := r.db.QueryRowContext(ctx, `
		UPDATE anomaly_events
		SET route_id = $2, tenant_id = $3, score = $4, reason = $5, features = $6
		WHERE id = $1
		RETURNING id::text, route_id::text, tenant_id::text, score::float8, reason, features, observed_at`,
		id, input.RouteID, input.TenantID, input.Score, input.Reason, features)
	return scanAnomaly(row)
}

func (r *PostgresRepository) DeleteAnomalyEvent(ctx context.Context, id string) error {
	result, err := r.db.ExecContext(ctx, `DELETE FROM anomaly_events WHERE id = $1`, id)
	return wrapExecResult(result, err)
}

type routeScanner interface {
	Scan(dest ...any) error
}

func scanRoute(scanner routeScanner) (models.Route, error) {
	var route models.Route
	var methods []string
	var scopes []string
	var headersRaw []byte
	err := scanner.Scan(
		&route.ID, &route.TenantID, &route.Name, &route.Host, &route.PathPrefix, pq.Array(&methods),
		&route.UpstreamService, &route.UpstreamProtocol, &route.RateLimitPerMin, pq.Array(&scopes),
		&headersRaw, &route.AnomalyProtection, &route.CreatedAt, &route.UpdatedAt,
	)
	if err != nil {
		return models.Route{}, wrapDBError(err)
	}
	route.Methods = methods
	route.RequiredScopes = scopes
	route.TransformHeaders = map[string]string{}
	if len(headersRaw) > 0 {
		if err := json.Unmarshal(headersRaw, &route.TransformHeaders); err != nil {
			return models.Route{}, fmt.Errorf("%w: decode route headers: %v", ErrRepository, err)
		}
	}
	return route, nil
}

func scanAnomaly(scanner routeScanner) (models.AnomalyEvent, error) {
	var event models.AnomalyEvent
	var featuresRaw []byte
	err := scanner.Scan(&event.ID, &event.RouteID, &event.TenantID, &event.Score, &event.Reason, &featuresRaw, &event.ObservedAt)
	if err != nil {
		return models.AnomalyEvent{}, wrapDBError(err)
	}
	event.Features = map[string]any{}
	if len(featuresRaw) > 0 {
		if err := json.Unmarshal(featuresRaw, &event.Features); err != nil {
			return models.AnomalyEvent{}, fmt.Errorf("%w: decode anomaly features: %v", ErrRepository, err)
		}
	}
	return event, nil
}

func wrapExecResult(result sql.Result, err error) error {
	if err != nil {
		return wrapDBError(err)
	}
	affected, err := result.RowsAffected()
	if err != nil {
		return wrapDBError(err)
	}
	if affected == 0 {
		return ErrNotFound
	}
	return nil
}

func wrapDBError(err error) error {
	if err == nil {
		return nil
	}
	if errors.Is(err, sql.ErrNoRows) {
		return ErrNotFound
	}
	var pqErr *pq.Error
	if errors.As(err, &pqErr) {
		if pqErr.Code == "23505" {
			return fmt.Errorf("%w: %s", ErrConflict, pqErr.Message)
		}
		if pqErr.Code == "23503" || pqErr.Code == "23514" || pqErr.Code == "22P02" {
			return fmt.Errorf("%w: %s", ErrInvalidRef, pqErr.Message)
		}
	}
	return fmt.Errorf("%w: %v", ErrRepository, err)
}

type MemoryRepository struct {
	mu        sync.RWMutex
	tenants   map[string]models.Tenant
	routes    map[string]models.Route
	anomalies map[string]models.AnomalyEvent
}

func NewMemoryRepository() *MemoryRepository {
	return &MemoryRepository{
		tenants:   map[string]models.Tenant{},
		routes:    map[string]models.Route{},
		anomalies: map[string]models.AnomalyEvent{},
	}
}

func (r *MemoryRepository) ListTenants(ctx context.Context, opts models.ListOptions) ([]models.Tenant, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	out := make([]models.Tenant, 0, len(r.tenants))
	for _, tenant := range r.tenants {
		out = append(out, tenant)
	}
	return page(out, opts), nil
}

func (r *MemoryRepository) GetTenant(ctx context.Context, id string) (models.Tenant, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	tenant, ok := r.tenants[id]
	if !ok {
		return models.Tenant{}, ErrNotFound
	}
	return tenant, nil
}

func (r *MemoryRepository) CreateTenant(ctx context.Context, input models.TenantInput) (models.Tenant, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	status := input.Status
	if status == "" {
		status = "active"
	}
	now := time.Now().UTC()
	tenant := models.Tenant{ID: newID("tenant"), Name: input.Name, Status: status, CreatedAt: now, UpdatedAt: now}
	r.tenants[tenant.ID] = tenant
	return tenant, nil
}

func (r *MemoryRepository) UpdateTenant(ctx context.Context, id string, input models.TenantInput) (models.Tenant, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	tenant, ok := r.tenants[id]
	if !ok {
		return models.Tenant{}, ErrNotFound
	}
	tenant.Name = input.Name
	if input.Status == "" {
		tenant.Status = "active"
	} else {
		tenant.Status = input.Status
	}
	tenant.UpdatedAt = time.Now().UTC()
	r.tenants[id] = tenant
	return tenant, nil
}

func (r *MemoryRepository) DeleteTenant(ctx context.Context, id string) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	if _, ok := r.tenants[id]; !ok {
		return ErrNotFound
	}
	delete(r.tenants, id)
	return nil
}

func (r *MemoryRepository) ListRoutes(ctx context.Context, tenantID string, opts models.ListOptions) ([]models.Route, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	out := []models.Route{}
	for _, route := range r.routes {
		if tenantID == "" || route.TenantID == tenantID {
			out = append(out, route)
		}
	}
	return page(out, opts), nil
}

func (r *MemoryRepository) GetRoute(ctx context.Context, id string) (models.Route, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	route, ok := r.routes[id]
	if !ok {
		return models.Route{}, ErrNotFound
	}
	return route, nil
}

func (r *MemoryRepository) CreateRoute(ctx context.Context, route models.Route) (models.Route, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if route.ID == "" {
		route.ID = newID("route")
	}
	now := time.Now().UTC()
	route.CreatedAt = now
	route.UpdatedAt = now
	r.routes[route.ID] = route
	return route, nil
}

func (r *MemoryRepository) UpdateRoute(ctx context.Context, route models.Route) (models.Route, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	existing, ok := r.routes[route.ID]
	if !ok {
		return models.Route{}, ErrNotFound
	}
	route.CreatedAt = existing.CreatedAt
	route.UpdatedAt = time.Now().UTC()
	r.routes[route.ID] = route
	return route, nil
}

func (r *MemoryRepository) DeleteRoute(ctx context.Context, id string) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	if _, ok := r.routes[id]; !ok {
		return ErrNotFound
	}
	delete(r.routes, id)
	return nil
}

func (r *MemoryRepository) MatchRoute(ctx context.Context, match models.RouteMatch) (models.Route, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	best := models.Route{}
	for _, route := range r.routes {
		if match.TenantID != "" && route.TenantID != match.TenantID {
			continue
		}
		if !strings.EqualFold(route.Host, match.Host) || !strings.HasPrefix(match.Path, route.PathPrefix) {
			continue
		}
		if !contains(route.Methods, strings.ToUpper(match.Method)) {
			continue
		}
		if len(route.PathPrefix) > len(best.PathPrefix) {
			best = route
		}
	}
	if best.ID == "" {
		return models.Route{}, ErrNotFound
	}
	return best, nil
}

func (r *MemoryRepository) ListAnomalyEvents(ctx context.Context, tenantID string, routeID string, opts models.ListOptions) ([]models.AnomalyEvent, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	out := []models.AnomalyEvent{}
	for _, event := range r.anomalies {
		if (tenantID == "" || event.TenantID == tenantID) && (routeID == "" || event.RouteID == routeID) {
			out = append(out, event)
		}
	}
	return page(out, opts), nil
}

func (r *MemoryRepository) GetAnomalyEvent(ctx context.Context, id string) (models.AnomalyEvent, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	event, ok := r.anomalies[id]
	if !ok {
		return models.AnomalyEvent{}, ErrNotFound
	}
	return event, nil
}

func (r *MemoryRepository) CreateAnomalyEvent(ctx context.Context, input models.AnomalyEventInput) (models.AnomalyEvent, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	event := models.AnomalyEvent{
		ID:         newID("anomaly"),
		RouteID:    input.RouteID,
		TenantID:   input.TenantID,
		Score:      input.Score,
		Reason:     input.Reason,
		Features:   input.Features,
		ObservedAt: time.Now().UTC(),
	}
	r.anomalies[event.ID] = event
	return event, nil
}

func (r *MemoryRepository) UpdateAnomalyEvent(ctx context.Context, id string, input models.AnomalyEventInput) (models.AnomalyEvent, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	event, ok := r.anomalies[id]
	if !ok {
		return models.AnomalyEvent{}, ErrNotFound
	}
	event.RouteID = input.RouteID
	event.TenantID = input.TenantID
	event.Score = input.Score
	event.Reason = input.Reason
	event.Features = input.Features
	r.anomalies[id] = event
	return event, nil
}

func (r *MemoryRepository) DeleteAnomalyEvent(ctx context.Context, id string) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	if _, ok := r.anomalies[id]; !ok {
		return ErrNotFound
	}
	delete(r.anomalies, id)
	return nil
}

func contains(values []string, target string) bool {
	for _, value := range values {
		if value == target {
			return true
		}
	}
	return false
}

func newID(prefix string) string {
	return fmt.Sprintf("%s_%d", prefix, time.Now().UTC().UnixNano())
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

func page[T any](values []T, opts models.ListOptions) []T {
	opts = normalizeListOptions(opts)
	if opts.Offset >= len(values) {
		return []T{}
	}
	end := opts.Offset + opts.Limit
	if end > len(values) {
		end = len(values)
	}
	return values[opts.Offset:end]
}
