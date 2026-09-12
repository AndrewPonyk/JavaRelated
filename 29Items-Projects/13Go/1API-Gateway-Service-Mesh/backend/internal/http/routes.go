package http

import (
	"errors"
	"net/http"
	"net/http/httputil"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/example/api-gateway/backend/internal/middleware"
	"github.com/example/api-gateway/backend/internal/models"
	"github.com/example/api-gateway/backend/internal/repository"
	"github.com/example/api-gateway/backend/internal/services"
	"github.com/gin-gonic/gin"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

type Server struct {
	service        *services.GatewayService
	anomalies      *services.AnomalyService
	limiter        *middleware.FixedWindowLimiter
	dataPlaneAuth  gin.HandlerFunc
	readinessCheck func() error
}

func NewServer(service *services.GatewayService, anomalies *services.AnomalyService, limiter *middleware.FixedWindowLimiter, dataPlaneAuth gin.HandlerFunc, readinessCheck func() error) *Server {
	return &Server{
		service:        service,
		anomalies:      anomalies,
		limiter:        limiter,
		dataPlaneAuth:  dataPlaneAuth,
		readinessCheck: readinessCheck,
	}
}

func (s *Server) Register(router *gin.Engine) {
	router.GET("/healthz", s.health)
	router.GET("/readyz", s.ready)
	router.GET("/metrics", gin.WrapH(promhttp.Handler()))

	api := router.Group("/api/v1")
	api.GET("/tenants", s.listTenants)
	api.POST("/tenants", s.createTenant)
	api.GET("/tenants/:id", s.getTenant)
	api.PUT("/tenants/:id", s.updateTenant)
	api.DELETE("/tenants/:id", s.deleteTenant)

	api.GET("/routes", s.listRoutes)
	api.POST("/routes", s.createRoute)
	api.GET("/routes/resolve", s.resolveRoute)
	api.GET("/routes/:id", s.getRoute)
	api.PUT("/routes/:id", s.updateRoute)
	api.DELETE("/routes/:id", s.deleteRoute)

	api.GET("/anomalies", s.listAnomalies)
	api.POST("/anomalies", s.createAnomaly)
	api.GET("/anomalies/:id", s.getAnomaly)
	api.PUT("/anomalies/:id", s.updateAnomaly)
	api.DELETE("/anomalies/:id", s.deleteAnomaly)
	api.POST("/traffic/score", s.scoreTraffic)

	router.NoRoute(s.dataPlaneAuth, s.proxyRequest)
}

func (s *Server) health(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{"status": "ok"})
}

func (s *Server) ready(c *gin.Context) {
	if s.readinessCheck != nil {
		if err := s.readinessCheck(); err != nil {
			c.JSON(http.StatusServiceUnavailable, gin.H{"status": "unavailable", "error": err.Error()})
			return
		}
	}
	c.JSON(http.StatusOK, gin.H{"status": "ready"})
}

func (s *Server) listTenants(c *gin.Context) {
	data, err := s.service.ListTenants(c.Request.Context(), pagination(c))
	respond(c, http.StatusOK, data, err)
}

func (s *Server) createTenant(c *gin.Context) {
	var input models.TenantInput
	if !bind(c, &input) {
		return
	}
	data, err := s.service.CreateTenant(c.Request.Context(), input)
	respond(c, http.StatusCreated, data, err)
}

func (s *Server) getTenant(c *gin.Context) {
	data, err := s.service.GetTenant(c.Request.Context(), c.Param("id"))
	respond(c, http.StatusOK, data, err)
}

func (s *Server) updateTenant(c *gin.Context) {
	var input models.TenantInput
	if !bind(c, &input) {
		return
	}
	data, err := s.service.UpdateTenant(c.Request.Context(), c.Param("id"), input)
	respond(c, http.StatusOK, data, err)
}

func (s *Server) deleteTenant(c *gin.Context) {
	respondEmpty(c, s.service.DeleteTenant(c.Request.Context(), c.Param("id")))
}

func (s *Server) listRoutes(c *gin.Context) {
	data, err := s.service.ListRoutes(c.Request.Context(), c.Query("tenantId"), pagination(c))
	respond(c, http.StatusOK, data, err)
}

func (s *Server) createRoute(c *gin.Context) {
	var input models.RouteInput
	if !bind(c, &input) {
		return
	}
	data, err := s.service.CreateRoute(c.Request.Context(), input)
	respond(c, http.StatusCreated, data, err)
}

func (s *Server) getRoute(c *gin.Context) {
	data, err := s.service.GetRoute(c.Request.Context(), c.Param("id"))
	respond(c, http.StatusOK, data, err)
}

func (s *Server) updateRoute(c *gin.Context) {
	var input models.RouteInput
	if !bind(c, &input) {
		return
	}
	data, err := s.service.UpdateRoute(c.Request.Context(), c.Param("id"), input)
	respond(c, http.StatusOK, data, err)
}

func (s *Server) deleteRoute(c *gin.Context) {
	respondEmpty(c, s.service.DeleteRoute(c.Request.Context(), c.Param("id")))
}

func (s *Server) resolveRoute(c *gin.Context) {
	data, err := s.service.MatchRoute(c.Request.Context(), models.RouteMatch{
		TenantID: c.Query("tenantId"),
		Host:     c.Query("host"),
		Path:     c.Query("path"),
		Method:   c.Query("method"),
	})
	respond(c, http.StatusOK, data, err)
}

func (s *Server) listAnomalies(c *gin.Context) {
	data, err := s.service.ListAnomalyEvents(c.Request.Context(), c.Query("tenantId"), c.Query("routeId"), pagination(c))
	respond(c, http.StatusOK, data, err)
}

func (s *Server) createAnomaly(c *gin.Context) {
	var input models.AnomalyEventInput
	if !bind(c, &input) {
		return
	}
	data, err := s.service.CreateAnomalyEvent(c.Request.Context(), input)
	respond(c, http.StatusCreated, data, err)
}

func (s *Server) getAnomaly(c *gin.Context) {
	data, err := s.service.GetAnomalyEvent(c.Request.Context(), c.Param("id"))
	respond(c, http.StatusOK, data, err)
}

func (s *Server) updateAnomaly(c *gin.Context) {
	var input models.AnomalyEventInput
	if !bind(c, &input) {
		return
	}
	data, err := s.service.UpdateAnomalyEvent(c.Request.Context(), c.Param("id"), input)
	respond(c, http.StatusOK, data, err)
}

func (s *Server) deleteAnomaly(c *gin.Context) {
	respondEmpty(c, s.service.DeleteAnomalyEvent(c.Request.Context(), c.Param("id")))
}

func (s *Server) scoreTraffic(c *gin.Context) {
	var input services.TrafficFeatures
	if !bind(c, &input) {
		return
	}
	data, err := s.anomalies.ScoreTraffic(c.Request.Context(), input)
	respond(c, http.StatusOK, data, err)
}

func (s *Server) proxyRequest(c *gin.Context) {
	route, err := s.service.MatchRoute(c.Request.Context(), models.RouteMatch{
		TenantID: c.GetHeader("X-Tenant-ID"),
		Host:     c.Request.Host,
		Path:     c.Request.URL.Path,
		Method:   c.Request.Method,
	})
	if err != nil {
		respondError(c, err)
		return
	}

	if !middleware.CheckScopes(route.RequiredScopes, c) {
		return
	}
	if !s.limiter.Allow(route.ID+":"+c.ClientIP(), route.RateLimitPerMin, time.Now().UTC()) {
		c.JSON(http.StatusTooManyRequests, gin.H{"error": gin.H{"message": "route rate limit exceeded"}})
		return
	}

	for key, value := range route.TransformHeaders {
		c.Request.Header.Set(key, value)
	}

	if route.UpstreamProtocol == "grpc" {
		c.JSON(http.StatusBadGateway, gin.H{"error": gin.H{"message": "gRPC routes must be reached through the Envoy listener"}})
		return
	}

	target, err := url.Parse(route.UpstreamService)
	if err != nil {
		c.JSON(http.StatusBadGateway, gin.H{"error": gin.H{"message": "invalid upstream service"}})
		return
	}
	proxy := httputil.NewSingleHostReverseProxy(target)
	originalDirector := proxy.Director
	proxy.Director = func(req *http.Request) {
		originalDirector(req)
		req.URL.Path = singleJoiningSlash(target.Path, strings.TrimPrefix(req.URL.Path, route.PathPrefix))
		req.Host = target.Host
	}
	proxy.ErrorHandler = func(w http.ResponseWriter, req *http.Request, err error) {
		c.JSON(http.StatusBadGateway, gin.H{"error": gin.H{"message": "upstream request failed"}})
	}
	proxy.ServeHTTP(c.Writer, c.Request)
}

func bind(c *gin.Context, target any) bool {
	if err := c.ShouldBindJSON(target); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": gin.H{"message": "invalid json payload"}})
		return false
	}
	return true
}

func respond(c *gin.Context, status int, data any, err error) {
	if err != nil {
		respondError(c, err)
		return
	}
	c.JSON(status, gin.H{"data": data})
}

func respondEmpty(c *gin.Context, err error) {
	if err != nil {
		respondError(c, err)
		return
	}
	c.Status(http.StatusNoContent)
}

func respondError(c *gin.Context, err error) {
	switch {
	case errors.Is(err, repository.ErrNotFound):
		c.JSON(http.StatusNotFound, gin.H{"error": gin.H{"message": "resource not found"}})
	case errors.Is(err, repository.ErrConflict):
		c.JSON(http.StatusConflict, gin.H{"error": gin.H{"message": err.Error()}})
	case errors.Is(err, repository.ErrInvalidRef):
		c.JSON(http.StatusBadRequest, gin.H{"error": gin.H{"message": err.Error()}})
	case errors.Is(err, services.ErrInvalidInput):
		c.JSON(http.StatusBadRequest, gin.H{"error": gin.H{"message": err.Error()}})
	default:
		c.JSON(http.StatusInternalServerError, gin.H{"error": gin.H{"message": "internal server error"}})
	}
}

func singleJoiningSlash(base string, path string) string {
	if base == "" {
		base = "/"
	}
	if path == "" {
		return base
	}
	return strings.TrimRight(base, "/") + "/" + strings.TrimLeft(path, "/")
}

func pagination(c *gin.Context) models.ListOptions {
	return models.ListOptions{
		Limit:  queryInt(c, "limit", 100),
		Offset: queryInt(c, "offset", 0),
	}
}

func queryInt(c *gin.Context, key string, fallback int) int {
	value := c.Query(key)
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		return fallback
	}
	return parsed
}
