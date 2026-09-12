package routes

import (
	"context"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/gin-contrib/cors"
	"github.com/gin-contrib/gzip"
	"github.com/gin-gonic/gin"

	"github.com/example/url-shortener-service/internal/http/handlers"
	"github.com/example/url-shortener-service/internal/http/middleware"
	"github.com/example/url-shortener-service/internal/qrcode"
	"github.com/example/url-shortener-service/internal/service"
)

type Dependencies struct {
	URLService    *service.URLService
	QRGenerator   *qrcode.Generator
	RateLimiter   middleware.RateLimiter
	Logger        *slog.Logger
	AllowedCORS   []string
	AdminAPIKeys  []string
	PublicBaseURL string
	HealthCheck   func(context.Context) error
}

func NewRouter(deps Dependencies) *gin.Engine {
	router := gin.New()
	router.Use(gin.Recovery())
	router.Use(middleware.RequestID())
	router.Use(middleware.Logging(deps.Logger))
	router.Use(gzip.Gzip(gzip.DefaultCompression))
	router.Use(cors.New(cors.Config{
		AllowOrigins:     deps.AllowedCORS,
		AllowMethods:     []string{http.MethodGet, http.MethodPost, http.MethodPatch, http.MethodDelete, http.MethodOptions},
		AllowHeaders:     []string{"Authorization", "Content-Type", "X-API-Key", "X-Request-ID"},
		ExposeHeaders:    []string{"X-Request-ID"},
		AllowCredentials: false,
	}))

	urlHandler := handlers.NewURLHandler(deps.URLService, deps.QRGenerator, deps.Logger, deps.AdminAPIKeys)

	api := router.Group("/api/v1")
	{
		api.GET("/health", func(c *gin.Context) {
			if deps.HealthCheck != nil {
				ctx, cancel := context.WithTimeout(c.Request.Context(), 2*time.Second)
				defer cancel()
				if err := deps.HealthCheck(ctx); err != nil {
					c.JSON(http.StatusServiceUnavailable, gin.H{"status": "unhealthy"})
					return
				}
			}
			c.JSON(http.StatusOK, gin.H{"status": "ok"})
		})
	}

	limited := api.Group("")
	limited.Use(middleware.RateLimit(deps.RateLimiter, "api"))
	{
		limited.POST("/urls", urlHandler.Create)
		limited.GET("/urls", middleware.APIKey(deps.AdminAPIKeys), urlHandler.List)
		limited.GET("/urls/:code", urlHandler.Get)
		limited.PATCH("/urls/:code", middleware.APIKey(deps.AdminAPIKeys), urlHandler.Update)
		limited.DELETE("/urls/:code", middleware.APIKey(deps.AdminAPIKeys), urlHandler.Delete)
		limited.GET("/urls/:code/analytics", middleware.APIKey(deps.AdminAPIKeys), urlHandler.Analytics)
		limited.GET("/urls/:code/qr", urlHandler.QR)
	}

	router.NoRoute(func(c *gin.Context) {
		if c.Request.Method != http.MethodGet {
			c.JSON(http.StatusNotFound, gin.H{"error": gin.H{"code": "not_found", "message": "route not found"}})
			return
		}

		path := strings.Trim(c.Request.URL.Path, "/")
		if path == "" || strings.Contains(path, "/") || path == "api" {
			c.JSON(http.StatusNotFound, gin.H{"error": gin.H{"code": "not_found", "message": "route not found"}})
			return
		}

		c.Params = append(c.Params, gin.Param{Key: "code", Value: path})
		urlHandler.Redirect(c)
	})

	return router
}
