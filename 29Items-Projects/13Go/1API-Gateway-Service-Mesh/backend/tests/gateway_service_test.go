package tests

import (
	"context"
	"errors"
	"testing"

	"github.com/example/api-gateway/backend/internal/models"
	"github.com/example/api-gateway/backend/internal/repository"
	"github.com/example/api-gateway/backend/internal/services"
)

func TestGatewayServiceRouteCRUDAndMatch(t *testing.T) {
	ctx := context.Background()
	repo := repository.NewMemoryRepository()
	service := services.NewGatewayService(repo)

	tenant, err := service.CreateTenant(ctx, models.TenantInput{Name: "acme"})
	if err != nil {
		t.Fatalf("create tenant: %v", err)
	}

	created, err := service.CreateRoute(ctx, models.RouteInput{
		TenantID:         tenant.ID,
		Name:             "orders",
		Host:             "api.example.com",
		PathPrefix:       "/orders",
		Methods:          []string{"get", "post"},
		UpstreamService:  "http://orders:8080",
		UpstreamProtocol: "http",
		RateLimitPerMin:  100,
	})
	if err != nil {
		t.Fatalf("create route: %v", err)
	}
	if created.ID == "" {
		t.Fatal("expected generated route id")
	}

	matched, err := service.MatchRoute(ctx, models.RouteMatch{
		TenantID: tenant.ID,
		Host:     "api.example.com",
		Path:     "/orders/123",
		Method:   "GET",
	})
	if err != nil {
		t.Fatalf("match route: %v", err)
	}
	if matched.ID != created.ID {
		t.Fatalf("expected route %s, got %s", created.ID, matched.ID)
	}

	updated, err := service.UpdateRoute(ctx, created.ID, models.RouteInput{
		TenantID:         tenant.ID,
		Name:             "orders-v2",
		Host:             "api.example.com",
		PathPrefix:       "/orders",
		Methods:          []string{"get"},
		UpstreamService:  "http://orders-v2:8080",
		UpstreamProtocol: "http",
		RateLimitPerMin:  50,
	})
	if err != nil {
		t.Fatalf("update route: %v", err)
	}
	if updated.Name != "orders-v2" {
		t.Fatalf("expected updated name, got %s", updated.Name)
	}

	if err := service.DeleteRoute(ctx, created.ID); err != nil {
		t.Fatalf("delete route: %v", err)
	}
	if _, err := service.GetRoute(ctx, created.ID); !errors.Is(err, repository.ErrNotFound) {
		t.Fatalf("expected not found after delete, got %v", err)
	}
}

func TestAnomalyServicePersistsAnomaly(t *testing.T) {
	ctx := context.Background()
	repo := repository.NewMemoryRepository()
	gateway := services.NewGatewayService(repo)
	anomalies := services.NewAnomalyService(gateway)

	tenant, err := gateway.CreateTenant(ctx, models.TenantInput{Name: "acme"})
	if err != nil {
		t.Fatalf("create tenant: %v", err)
	}
	route, err := gateway.CreateRoute(ctx, models.RouteInput{
		TenantID:         tenant.ID,
		Name:             "checkout",
		Host:             "api.example.com",
		PathPrefix:       "/checkout",
		Methods:          []string{"POST"},
		UpstreamService:  "http://checkout:8080",
		UpstreamProtocol: "http",
	})
	if err != nil {
		t.Fatalf("create route: %v", err)
	}

	decision, err := anomalies.ScoreTraffic(ctx, services.TrafficFeatures{
		RouteID:      route.ID,
		TenantID:     tenant.ID,
		ErrorRate:    0.4,
		P95LatencyMS: 3000,
		RequestRate:  2000,
	})
	if err != nil {
		t.Fatalf("score traffic: %v", err)
	}
	if !decision.IsAnomaly {
		t.Fatal("expected anomaly decision")
	}

	events, err := gateway.ListAnomalyEvents(ctx, tenant.ID, route.ID, models.ListOptions{})
	if err != nil {
		t.Fatalf("list anomalies: %v", err)
	}
	if len(events) != 1 {
		t.Fatalf("expected 1 anomaly event, got %d", len(events))
	}
}

func TestGatewayServiceRejectsInvalidRoute(t *testing.T) {
	service := services.NewGatewayService(repository.NewMemoryRepository())
	_, err := service.CreateRoute(context.Background(), models.RouteInput{
		TenantID:         "tenant_1",
		Name:             "bad",
		Host:             "api.example.com",
		PathPrefix:       "missing-slash",
		Methods:          []string{"GET"},
		UpstreamService:  "not-a-url",
		UpstreamProtocol: "http",
	})
	if !errors.Is(err, services.ErrInvalidInput) {
		t.Fatalf("expected invalid input, got %v", err)
	}
}

func TestGatewayServicePaginatesTenants(t *testing.T) {
	ctx := context.Background()
	service := services.NewGatewayService(repository.NewMemoryRepository())
	for _, name := range []string{"a", "b", "c"} {
		if _, err := service.CreateTenant(ctx, models.TenantInput{Name: name}); err != nil {
			t.Fatalf("create tenant %s: %v", name, err)
		}
	}

	tenants, err := service.ListTenants(ctx, models.ListOptions{Limit: 2})
	if err != nil {
		t.Fatalf("list tenants: %v", err)
	}
	if len(tenants) != 2 {
		t.Fatalf("expected 2 tenants, got %d", len(tenants))
	}
}
