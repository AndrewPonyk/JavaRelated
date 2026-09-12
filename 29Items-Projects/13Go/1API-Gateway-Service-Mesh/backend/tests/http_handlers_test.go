package tests

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	gatewayhttp "github.com/example/api-gateway/backend/internal/http"
	"github.com/example/api-gateway/backend/internal/middleware"
	"github.com/example/api-gateway/backend/internal/repository"
	"github.com/example/api-gateway/backend/internal/services"
	"github.com/gin-gonic/gin"
)

func TestTenantAPIFlow(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := gin.New()
	repo := repository.NewMemoryRepository()
	service := services.NewGatewayService(repo)
	server := gatewayhttp.NewServer(service, services.NewAnomalyService(service), middleware.NewFixedWindowLimiter(), middleware.DataPlaneJWTAuth("secret", false), nil)
	server.Register(router)

	body := bytes.NewBufferString(`{"name":"acme","status":"active"}`)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/tenants", body)
	req.Header.Set("Content-Type", "application/json")
	res := httptest.NewRecorder()
	router.ServeHTTP(res, req)
	if res.Code != http.StatusCreated {
		t.Fatalf("expected 201, got %d: %s", res.Code, res.Body.String())
	}

	var envelope struct {
		Data struct {
			ID string `json:"id"`
		} `json:"data"`
	}
	if err := json.Unmarshal(res.Body.Bytes(), &envelope); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if envelope.Data.ID == "" {
		t.Fatal("expected tenant id")
	}

	req = httptest.NewRequest(http.MethodGet, "/api/v1/tenants/"+envelope.Data.ID, nil)
	res = httptest.NewRecorder()
	router.ServeHTTP(res, req)
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
}

func TestTenantAPIRejectsInvalidJSON(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := gin.New()
	repo := repository.NewMemoryRepository()
	service := services.NewGatewayService(repo)
	server := gatewayhttp.NewServer(service, services.NewAnomalyService(service), middleware.NewFixedWindowLimiter(), middleware.DataPlaneJWTAuth("secret", false), nil)
	server.Register(router)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/tenants", bytes.NewBufferString(`{`))
	req.Header.Set("Content-Type", "application/json")
	res := httptest.NewRecorder()
	router.ServeHTTP(res, req)
	if res.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d: %s", res.Code, res.Body.String())
	}
}
