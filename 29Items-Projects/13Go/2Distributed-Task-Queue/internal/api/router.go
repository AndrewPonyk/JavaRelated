package api

import (
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

func NewRouter(handler *Handler, logger *slog.Logger, apiKey string, enforceHTTPS bool) http.Handler {
	r := chi.NewRouter()
	r.Use(middleware.RequestID)
	r.Use(middleware.Recoverer)
	r.Use(middleware.Compress(5, "application/json", "text/html", "text/css", "application/javascript", "text/javascript"))
	r.Use(EnforceHTTPS(enforceHTTPS))
	r.Use(SecurityHeaders)
	r.Use(RequestLogger(logger))

	r.Get("/healthz", Health)
	r.Handle("/metrics", promhttp.Handler())

	r.Route("/api/v1", func(r chi.Router) {
		r.Use(RateLimit(120, time.Minute))
		r.Use(APIKeyAuth(apiKey))
		r.Get("/queue/stats", handler.QueueStats)
		r.Get("/dead-letters", handler.DeadLetters)
		r.Post("/dead-letters/{id}/requeue", handler.RequeueDeadLetter)

		r.Route("/tasks", func(r chi.Router) {
			r.Post("/", handler.CreateTask)
			r.Get("/", handler.ListTasks)
			r.Get("/{id}", handler.GetTask)
			r.Put("/{id}", handler.UpdateTask)
			r.Post("/{id}/cancel", handler.CancelTask)
			r.Delete("/{id}", handler.DeleteTask)
			r.Get("/{id}/attempts", handler.TaskAttempts)
		})
	})

	components := http.StripPrefix("/components/", http.FileServer(http.Dir("web/components")))
	r.Handle("/components/*", components)

	fs := http.FileServer(http.Dir("web/static"))
	r.Handle("/*", fs)

	return r
}
