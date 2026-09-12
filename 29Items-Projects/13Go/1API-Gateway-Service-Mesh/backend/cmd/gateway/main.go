package main

import (
	"context"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/example/api-gateway/backend/internal/config"
	"github.com/example/api-gateway/backend/internal/database"
	gatewayhttp "github.com/example/api-gateway/backend/internal/http"
	"github.com/example/api-gateway/backend/internal/middleware"
	"github.com/example/api-gateway/backend/internal/repository"
	"github.com/example/api-gateway/backend/internal/services"
	"github.com/gin-contrib/gzip"
	"github.com/gin-gonic/gin"
	"github.com/prometheus/client_golang/prometheus"
)

func main() {
	if len(os.Args) > 1 && os.Args[1] == "--healthcheck" {
		runHealthcheck()
		return
	}

	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))

	cfg, err := config.Load()
	if err != nil {
		logger.Error("invalid configuration", "error", err)
		os.Exit(1)
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	db, err := database.Connect(ctx, cfg.DatabaseURL)
	if err != nil {
		logger.Error("database connection failed", "error", err)
		os.Exit(1)
	}
	defer db.Close()

	if err := database.Migrate(ctx, db); err != nil {
		logger.Error("database migration failed", "error", err)
		os.Exit(1)
	}

	gatewayService := services.NewGatewayService(repository.NewPostgresRepository(db))
	anomalyService := services.NewAnomalyService(gatewayService)
	limiter := middleware.NewFixedWindowLimiter()

	requestCounter := prometheus.NewCounterVec(prometheus.CounterOpts{
		Name: "api_gateway_http_requests_total",
		Help: "Total HTTP requests handled by the gateway.",
	}, []string{"method", "path", "status"})
	latencyHistogram := prometheus.NewHistogramVec(prometheus.HistogramOpts{
		Name:    "api_gateway_http_request_duration_seconds",
		Help:    "HTTP request latency in seconds.",
		Buckets: prometheus.DefBuckets,
	}, []string{"method", "path"})
	prometheus.MustRegister(requestCounter, latencyHistogram)

	router := gin.New()
	router.Use(gin.Recovery())
	router.Use(gzip.Gzip(gzip.DefaultCompression))
	router.Use(middleware.SecurityHeaders())
	router.Use(middleware.RequestBodyLimit(cfg.MaxRequestBodyBytes))
	router.Use(middleware.RequireJSONForWrites())
	router.Use(middleware.RequestLogger(logger))
	router.Use(middleware.PrometheusMetrics(requestCounter, latencyHistogram))

	admin := middleware.AdminAPIKeyAuth(cfg.AdminAPIKey)
	router.Use(func(c *gin.Context) {
		if c.Request.URL.Path == "/healthz" || c.Request.URL.Path == "/readyz" || c.Request.URL.Path == "/metrics" {
			c.Next()
			return
		}
		if strings.HasPrefix(c.Request.URL.Path, "/api/") {
			admin(c)
			return
		}
		c.Next()
	})

	server := gatewayhttp.NewServer(
		gatewayService,
		anomalyService,
		limiter,
		middleware.DataPlaneJWTAuth(cfg.JWTSharedSecret, cfg.AuthMode == "strict"),
		func() error {
			ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
			defer cancel()
			return db.PingContext(ctx)
		},
	)
	server.Register(router)

	httpServer := &http.Server{
		Addr:         cfg.HTTPAddr,
		Handler:      router,
		ReadTimeout:  time.Duration(cfg.ReadTimeoutSeconds) * time.Second,
		WriteTimeout: time.Duration(cfg.WriteTimeoutSeconds) * time.Second,
	}

	go func() {
		logger.Info("starting api gateway", "addr", cfg.HTTPAddr, "env", cfg.Environment)
		if err := httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Error("http server failed", "error", err)
			stop()
		}
	}()

	<-ctx.Done()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), time.Duration(cfg.ShutdownTimeoutSeconds)*time.Second)
	defer cancel()
	if err := httpServer.Shutdown(shutdownCtx); err != nil {
		logger.Error("graceful shutdown failed", "error", err)
	}
}

func runHealthcheck() {
	client := http.Client{Timeout: 2 * time.Second}
	resp, err := client.Get("http://127.0.0.1:8080/healthz")
	if err != nil {
		os.Exit(1)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		os.Exit(1)
	}
}
